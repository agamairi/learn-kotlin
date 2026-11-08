import * as SQLite from 'expo-sqlite'
import type {
  DailyActivity,
  ExerciseResult,
  ProgressCommit,
  ProgressRepository,
  ReviewCard,
  LessonProgressSettlement,
  SettingsRepository,
  CourseRepository,
  Course,
} from '@learn/core'
import {
  BACKUP_FORMAT,
  BACKUP_SCHEMA_VERSION,
  parseBackup,
  type LearnCodeBackup,
  type BackupAttemptHistory,
} from '@learn/storage'

type ExerciseRow = {
  exercise_id: string
  problem_id: string | null
  correct: number
  xp_awarded: number
  epoch_millis: number
  lesson_id: string | null
  chapter_id: string | null
}

type AttemptRow = ExerciseRow & {
  attempt_id: string
  course_id: string | null
  is_retry: number
}

type DailyActivityRow = {
  epoch_day: number
  xp_earned: number
  exercises_completed: number
  goal_met: number
}

type ReviewRow = {
  item_id: string
  box: number
  due_epoch_day: number
}

type CompletionRow = {
  lesson_id: string
  completed_at_epoch_millis: number
}

type XpLedgerRow = {
  entry_id: string
  amount: number
  epoch_millis: number
}

export interface MobileProgressSnapshot {
  totalXp: number
  xpToday: number
  completedLessonIds: string[]
  reviewCards: ReviewCard[]
  currentStreak: number
  longestStreak: number
  freezesHeld: number
  dailyGoalXp: number
}

export class SQLiteProgressRepository implements ProgressRepository, SettingsRepository {
  private constructor(private readonly db: SQLite.SQLiteDatabase) {}

  static async open(): Promise<SQLiteProgressRepository> {
    const db = await SQLite.openDatabaseAsync('learn-code.db')
    const repository = new SQLiteProgressRepository(db)
    await repository.initialize()
    return repository
  }

  private async initialize(): Promise<void> {
    await this.db.execAsync(`
      PRAGMA journal_mode = WAL;
      CREATE TABLE IF NOT EXISTS exercise_results (
        exercise_id TEXT NOT NULL,
        problem_id TEXT,
        correct INTEGER NOT NULL,
        xp_awarded INTEGER NOT NULL,
        epoch_millis INTEGER NOT NULL,
        lesson_id TEXT,
        chapter_id TEXT,
        PRIMARY KEY (exercise_id, epoch_millis, correct, xp_awarded)
      );
      CREATE TABLE IF NOT EXISTS attempt_history (
        attempt_id TEXT PRIMARY KEY,
        exercise_id TEXT NOT NULL,
        problem_id TEXT,
        correct INTEGER NOT NULL,
        xp_awarded INTEGER NOT NULL,
        epoch_millis INTEGER NOT NULL,
        lesson_id TEXT,
        chapter_id TEXT,
        course_id TEXT,
        is_retry INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS xp_ledger (
        entry_id TEXT PRIMARY KEY,
        amount INTEGER NOT NULL,
        epoch_millis INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS daily_activity (
        epoch_day INTEGER PRIMARY KEY,
        xp_earned INTEGER NOT NULL,
        exercises_completed INTEGER NOT NULL,
        goal_met INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS review_cards (
        item_id TEXT PRIMARY KEY,
        box INTEGER NOT NULL,
        due_epoch_day INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS lesson_completions (
        lesson_id TEXT PRIMARY KEY,
        completed_at_epoch_millis INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS frozen_days (
        epoch_day INTEGER PRIMARY KEY
      );
      CREATE TABLE IF NOT EXISTS settled_runs (
        run_id TEXT PRIMARY KEY
      );
      CREATE TABLE IF NOT EXISTS metadata (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      );
    `)
    // Additive migrations for databases created by the first beta builds.
    try { await this.db.execAsync('ALTER TABLE exercise_results ADD COLUMN problem_id TEXT') } catch { /* already present */ }
    try { await this.db.execAsync('ALTER TABLE attempt_history ADD COLUMN problem_id TEXT') } catch { /* already present */ }
    const existingGoal = await this.db.getFirstAsync<{ value: string }>("SELECT value FROM metadata WHERE key = 'daily_goal_xp'")
    if (!existingGoal) await this.db.runAsync("INSERT INTO metadata (key, value) VALUES ('daily_goal_xp', '30')")
  }

  async recordExerciseResult(result: ExerciseResult): Promise<void> {
    await this.db.runAsync(
      `INSERT OR IGNORE INTO exercise_results (exercise_id, problem_id, correct, xp_awarded, epoch_millis, lesson_id, chapter_id) VALUES (?, ?, ?, ?, ?, ?, ?)`,
      result.exerciseId,
      result.problemId ?? null,
      result.correct ? 1 : 0,
      result.xpAwarded,
      result.epochMillis,
      result.lessonId ?? null,
      result.chapterId ?? null,
    )
  }

  async recordAttemptHistory(attempt: BackupAttemptHistory): Promise<void> {
    await this.db.runAsync(
      `INSERT OR IGNORE INTO attempt_history (attempt_id, exercise_id, problem_id, correct, xp_awarded, epoch_millis, lesson_id, chapter_id, course_id, is_retry) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      attempt.attemptId,
      attempt.exerciseId,
      attempt.problemId ?? null,
      attempt.correct ? 1 : 0,
      attempt.xpAwarded,
      attempt.epochMillis,
      attempt.lessonId ?? null,
      attempt.chapterId ?? null,
      attempt.courseId ?? null,
      attempt.isRetry ? 1 : 0,
    )
  }

  async commitProgress(commit: ProgressCommit): Promise<boolean> {
    const existing = await this.db.getFirstAsync<{ value: number }>('SELECT 1 AS value FROM xp_ledger WHERE entry_id = ?', `attempt:${commit.attemptId}`)
    if (existing) return false
    await this.db.withTransactionAsync(async () => {
      await this.db.runAsync(
        `INSERT OR IGNORE INTO exercise_results (exercise_id, problem_id, correct, xp_awarded, epoch_millis, lesson_id, chapter_id) VALUES (?, ?, ?, ?, ?, ?, ?)`,
        commit.result.exerciseId,
        commit.result.problemId ?? null,
        commit.result.correct ? 1 : 0,
        commit.result.xpAwarded,
        commit.result.epochMillis,
        commit.result.lessonId ?? null,
        commit.result.chapterId ?? null,
      )
      await this.db.runAsync('INSERT INTO xp_ledger (entry_id, amount, epoch_millis) VALUES (?, ?, ?)', `attempt:${commit.attemptId}`, commit.result.xpAwarded, commit.result.epochMillis)
      await this.upsertDailyActivity(commit.dailyActivity)
      if (commit.reviewCard) await this.upsertReviewCard(commit.reviewCard)
      for (const epochDay of commit.frozenEpochDays ?? []) await this.db.runAsync('INSERT OR IGNORE INTO frozen_days (epoch_day) VALUES (?)', epochDay)
      if (commit.heldStreakFreezes != null) await this.setHeldStreakFreezes(commit.heldStreakFreezes)
    })
    return true
  }

  async settleLessonProgress(settlement: LessonProgressSettlement): Promise<boolean> {
    const existing = await this.db.getFirstAsync<{ value: number }>('SELECT 1 AS value FROM settled_runs WHERE run_id = ?', settlement.runId)
    if (existing) return false
    await this.db.withTransactionAsync(async () => {
      await this.db.runAsync('INSERT INTO settled_runs (run_id) VALUES (?)', settlement.runId)
      for (const attempt of settlement.attempts) {
        await this.db.runAsync(
          `INSERT OR IGNORE INTO exercise_results (exercise_id, problem_id, correct, xp_awarded, epoch_millis, lesson_id, chapter_id) VALUES (?, ?, ?, ?, ?, ?, ?)`,
          attempt.result.exerciseId,
          attempt.result.problemId ?? null,
          attempt.result.correct ? 1 : 0,
          attempt.result.xpAwarded,
          attempt.result.epochMillis,
          attempt.result.lessonId ?? null,
          attempt.result.chapterId ?? null,
        )
        await this.db.runAsync('INSERT OR IGNORE INTO xp_ledger (entry_id, amount, epoch_millis) VALUES (?, ?, ?)', `attempt:${attempt.attemptId}`, attempt.result.xpAwarded, attempt.result.epochMillis)
      }
      if (settlement.paceBonusXp > 0) await this.db.runAsync('INSERT INTO xp_ledger (entry_id, amount, epoch_millis) VALUES (?, ?, ?)', `lesson-bonus:${settlement.runId}`, settlement.paceBonusXp, settlement.completedAtEpochMillis)
      if (settlement.dailyActivity) await this.upsertDailyActivity(settlement.dailyActivity)
      for (const card of settlement.reviewCards) {
        await this.db.runAsync('INSERT OR IGNORE INTO review_cards (item_id, box, due_epoch_day) VALUES (?, ?, ?)', card.itemId, card.box, card.dueEpochDay)
      }
      for (const epochDay of settlement.frozenEpochDays) await this.db.runAsync('INSERT OR IGNORE INTO frozen_days (epoch_day) VALUES (?)', epochDay)
      await this.setHeldStreakFreezes(settlement.heldStreakFreezes)
      await this.db.runAsync('INSERT OR REPLACE INTO lesson_completions (lesson_id, completed_at_epoch_millis) VALUES (?, ?)', settlement.lessonId, settlement.completedAtEpochMillis)
    })
    return true
  }

  async getExerciseResults(): Promise<ExerciseResult[]> {
    const rows = await this.db.getAllAsync<ExerciseRow>('SELECT * FROM exercise_results ORDER BY epoch_millis ASC')
    return rows.map(toExerciseResult)
  }

  async getExerciseResultsForExercise(exerciseId: string): Promise<ExerciseResult[]> {
    const rows = await this.db.getAllAsync<ExerciseRow>('SELECT * FROM exercise_results WHERE exercise_id = ? ORDER BY epoch_millis ASC', exerciseId)
    return rows.map(toExerciseResult)
  }

  async getExerciseResultsForLesson(lessonId: string): Promise<ExerciseResult[]> {
    const rows = await this.db.getAllAsync<ExerciseRow>('SELECT * FROM exercise_results WHERE lesson_id = ? ORDER BY epoch_millis ASC', lessonId)
    return rows.map(toExerciseResult)
  }

  async getDailyActivities(): Promise<DailyActivity[]> {
    const rows = await this.db.getAllAsync<DailyActivityRow>('SELECT * FROM daily_activity ORDER BY epoch_day ASC')
    return rows.map(toDailyActivity)
  }

  async getDailyActivity(epochDay: number): Promise<DailyActivity | null> {
    const row = await this.db.getFirstAsync<DailyActivityRow>('SELECT * FROM daily_activity WHERE epoch_day = ?', epochDay)
    return row ? toDailyActivity(row) : null
  }

  private async upsertDailyActivity(activity: DailyActivity): Promise<void> {
    await this.db.runAsync(
      `INSERT OR REPLACE INTO daily_activity (epoch_day, xp_earned, exercises_completed, goal_met) VALUES (?, ?, ?, ?)`,
      activity.epochDay,
      activity.xpEarned,
      activity.exercisesCompleted,
      activity.goalMet ? 1 : 0,
    )
  }

  async upsertReviewCard(card: ReviewCard): Promise<void> {
    await this.db.runAsync('INSERT OR REPLACE INTO review_cards (item_id, box, due_epoch_day) VALUES (?, ?, ?)', card.itemId, card.box, card.dueEpochDay)
  }

  async getReviewCard(itemId: string): Promise<ReviewCard | null> {
    const row = await this.db.getFirstAsync<ReviewRow>('SELECT * FROM review_cards WHERE item_id = ?', itemId)
    return row ? toReviewCard(row) : null
  }

  async getDueReviewCards(epochDay: number): Promise<ReviewCard[]> {
    const rows = await this.db.getAllAsync<ReviewRow>('SELECT * FROM review_cards WHERE due_epoch_day <= ? ORDER BY due_epoch_day ASC', epochDay)
    return rows.map(toReviewCard)
  }

  async markLessonCompleted(lessonId: string, completedAtEpochMillis: number): Promise<void> {
    await this.db.runAsync('INSERT OR REPLACE INTO lesson_completions (lesson_id, completed_at_epoch_millis) VALUES (?, ?)', lessonId, completedAtEpochMillis)
  }

  async isLessonCompleted(lessonId: string): Promise<boolean> {
    const row = await this.db.getFirstAsync<{ value: number }>('SELECT 1 AS value FROM lesson_completions WHERE lesson_id = ?', lessonId)
    return Boolean(row)
  }

  async getCompletedLessonIds(): Promise<Set<string>> {
    const rows = await this.db.getAllAsync<{ lesson_id: string }>('SELECT lesson_id FROM lesson_completions')
    return new Set(rows.map((row) => row.lesson_id))
  }

  async getFrozenEpochDays(): Promise<number[]> {
    const rows = await this.db.getAllAsync<{ epoch_day: number }>('SELECT epoch_day FROM frozen_days ORDER BY epoch_day ASC')
    return rows.map((row) => row.epoch_day)
  }

  async recordStreakFreeze(epochDay: number): Promise<void> {
    await this.db.runAsync('INSERT OR IGNORE INTO frozen_days (epoch_day) VALUES (?)', epochDay)
  }

  async getHeldStreakFreezes(): Promise<number> {
    const row = await this.db.getFirstAsync<{ value: string }>("SELECT value FROM metadata WHERE key = 'held_streak_freezes'")
    return row ? Number(row.value) : 0
  }

  async setHeldStreakFreezes(count: number): Promise<void> {
    await this.db.runAsync("INSERT OR REPLACE INTO metadata (key, value) VALUES ('held_streak_freezes', ?)", String(Math.max(0, Math.min(2, Math.round(count)))))
  }

  async getDailyGoalXp(): Promise<number> {
    const row = await this.db.getFirstAsync<{ value: string }>("SELECT value FROM metadata WHERE key = 'daily_goal_xp'")
    return row ? Number(row.value) : 30
  }

  async setDailyGoalXp(xp: number): Promise<void> {
    if (!Number.isFinite(xp) || xp < 0 || xp > 10000) throw new Error('Daily goal XP must be between 0 and 10000')
    await this.db.runAsync("INSERT OR REPLACE INTO metadata (key, value) VALUES ('daily_goal_xp', ?)", String(Math.round(xp)))
  }

  async setPreference(key: string, value: string): Promise<void> {
    await this.db.runAsync('INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)', `preference:${key}`, value)
  }

  async getPreference(key: string): Promise<string | null> {
    const row = await this.db.getFirstAsync<{ value: string }>('SELECT value FROM metadata WHERE key = ?', `preference:${key}`)
    return row?.value ?? null
  }

  async getSnapshot(todayEpochDay: number, streak: { currentStreak: number; longestStreak: number }): Promise<MobileProgressSnapshot> {
    const total = await this.db.getFirstAsync<{ total: number | null }>('SELECT SUM(amount) AS total FROM xp_ledger')
    const today = await this.getDailyActivity(todayEpochDay)
    return {
      totalXp: total?.total ?? 0,
      xpToday: today?.xpEarned ?? 0,
      completedLessonIds: [...(await this.getCompletedLessonIds())],
      reviewCards: await this.getAllReviewCards(),
      currentStreak: streak.currentStreak,
      longestStreak: streak.longestStreak,
      freezesHeld: await this.getHeldStreakFreezes(),
      dailyGoalXp: await this.getDailyGoalXp(),
    }
  }

  private async getAllReviewCards(): Promise<ReviewCard[]> {
    const rows = await this.db.getAllAsync<ReviewRow>('SELECT * FROM review_cards ORDER BY due_epoch_day ASC')
    return rows.map(toReviewCard)
  }

  async createBackup(appVersion: string, contentPacks: Array<{ packId: string; version: string }>, selectedLanguage: string): Promise<LearnCodeBackup> {
    const attempts = await this.db.getAllAsync<AttemptRow>('SELECT * FROM attempt_history ORDER BY epoch_millis ASC')
    const results = await this.getExerciseResults()
    const xpLedger = await this.db.getAllAsync<XpLedgerRow>('SELECT * FROM xp_ledger ORDER BY epoch_millis ASC')
    const activities = await this.getDailyActivities()
    const cards = await this.getAllReviewCards()
    const completions = await this.db.getAllAsync<CompletionRow>('SELECT * FROM lesson_completions ORDER BY completed_at_epoch_millis ASC')
    const storedHearts = await this.getPreference('hearts')
    const storedRefill = await this.getPreference('heart_refill_at')
    const hearts = storedHearts == null ? 5 : Math.max(0, Math.min(5, Math.round(Number(storedHearts))))
    const heartRefillAt = storedRefill ? Number(storedRefill) : null
    return {
      format: BACKUP_FORMAT,
      schemaVersion: BACKUP_SCHEMA_VERSION,
      createdAt: new Date().toISOString(),
      app: { version: appVersion, contentPacks },
      data: {
        attemptHistory: attempts.map((row) => ({
          attemptId: row.attempt_id,
          exerciseId: row.exercise_id,
          problemId: row.problem_id,
          correct: Boolean(row.correct),
          xpAwarded: row.xp_awarded,
          epochMillis: row.epoch_millis,
          lessonId: row.lesson_id,
          chapterId: row.chapter_id,
          courseId: row.course_id,
          isRetry: Boolean(row.is_retry),
        })),
        exerciseResults: results,
        xpLedger: xpLedger.map((row) => ({ entryId: row.entry_id, amount: row.amount, epochMillis: row.epoch_millis })),
        dailyActivities: activities,
        reviewCards: cards,
        completedLessons: completions.map((row) => ({ lessonId: row.lesson_id, completedAtEpochMillis: row.completed_at_epoch_millis })),
        frozenEpochDays: await this.getFrozenEpochDays(),
        heldStreakFreezes: await this.getHeldStreakFreezes(),
        dailyGoalXp: await this.getDailyGoalXp(),
        preferences: { selectedLanguage, hearts, heartRefillAt: Number.isFinite(heartRefillAt) ? heartRefillAt : null },
      },
    }
  }

  async restoreBackup(serialized: string): Promise<LearnCodeBackup> {
    const backup = parseBackup(serialized)
    await this.db.withTransactionAsync(async () => {
      await this.clearProgressTables()
      for (const attempt of backup.data.attemptHistory) await this.recordAttemptHistory(attempt)
      for (const result of backup.data.exerciseResults) await this.recordExerciseResult(result)
      const xpLedger = backup.data.xpLedger ?? backup.data.attemptHistory.map((attempt) => ({
        entryId: `attempt:${attempt.attemptId}`,
        amount: attempt.xpAwarded,
        epochMillis: attempt.epochMillis,
      }))
      for (const entry of xpLedger) {
        await this.db.runAsync('INSERT OR IGNORE INTO xp_ledger (entry_id, amount, epoch_millis) VALUES (?, ?, ?)', entry.entryId, entry.amount, entry.epochMillis)
      }
      for (const activity of backup.data.dailyActivities) await this.upsertDailyActivity(activity)
      for (const card of backup.data.reviewCards) await this.upsertReviewCard(card)
      for (const lesson of backup.data.completedLessons) await this.markLessonCompleted(lesson.lessonId, lesson.completedAtEpochMillis)
      for (const epochDay of backup.data.frozenEpochDays) await this.recordStreakFreeze(epochDay)
      await this.setHeldStreakFreezes(backup.data.heldStreakFreezes)
      await this.setDailyGoalXp(backup.data.dailyGoalXp)
      await this.setPreference('selectedLanguage', backup.data.preferences.selectedLanguage)
      await this.setPreference('hearts', String(backup.data.preferences.hearts ?? 5))
      await this.setPreference('heart_refill_at', backup.data.preferences.heartRefillAt == null ? '' : String(backup.data.preferences.heartRefillAt))
    })
    return backup
  }

  async clearProgress(): Promise<void> {
    await this.db.withTransactionAsync(async () => this.clearProgressTables())
  }

  private async clearProgressTables(): Promise<void> {
    await this.db.execAsync('DELETE FROM exercise_results; DELETE FROM attempt_history; DELETE FROM xp_ledger; DELETE FROM daily_activity; DELETE FROM review_cards; DELETE FROM lesson_completions; DELETE FROM frozen_days; DELETE FROM settled_runs;')
    await this.db.runAsync("INSERT OR REPLACE INTO metadata (key, value) VALUES ('held_streak_freezes', '0')")
  }
}

export class SQLiteCourseRepository implements CourseRepository {
  constructor(private readonly courses: Course[]) {}

  getCourse(courseId: string): Course | null {
    return this.courses.find((course) => course.id === courseId) ?? null
  }

  listCourses(): Course[] {
    return [...this.courses]
  }
}

function toExerciseResult(row: ExerciseRow): ExerciseResult {
  return {
    exerciseId: row.exercise_id,
    problemId: row.problem_id,
    correct: Boolean(row.correct),
    xpAwarded: row.xp_awarded,
    epochMillis: row.epoch_millis,
    lessonId: row.lesson_id,
    chapterId: row.chapter_id,
  }
}

function toDailyActivity(row: DailyActivityRow): DailyActivity {
  return {
    epochDay: row.epoch_day,
    xpEarned: row.xp_earned,
    exercisesCompleted: row.exercises_completed,
    goalMet: Boolean(row.goal_met),
  }
}

function toReviewCard(row: ReviewRow): ReviewCard {
  return { itemId: row.item_id, box: row.box, dueEpochDay: row.due_epoch_day }
}
