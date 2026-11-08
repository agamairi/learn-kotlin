import type { Exercise, MultiPartProblemExercise } from '../models/exercise.ts';
import type { DailyActivity, ExerciseResult, ReviewCard, StreakFreezeState } from '../models/progress.ts';
import {
  effectiveEpochDay,
  effectiveLocalDate,
  fromEpochDay,
  toEpochDay,
} from './date-time.ts';
import { LeitnerScheduler, type ReviewScheduler } from './leitner.ts';
import { DefaultStreakCalculator, type StreakCalculator } from './streak.ts';
import { DefaultXpPolicy, type XpPolicy } from './xp-policy.ts';
import type {
  CourseRepository,
  LessonProgressSettlement,
  ProgressCommit,
  ProgressRepository,
  SettingsRepository,
  StagedLessonAttempt,
} from './repository.ts';

export interface ExerciseAttempt {
  attemptId: string;
  exerciseId: string;
  correct: boolean;
  review?: boolean;
  aiHintsUsed?: number[];
  /** A retry within a lesson's mistake round. */
  isLessonRetry?: boolean;
  /** Present only when this attempt belongs to an active lesson run. */
  lessonRunId?: string | null;
}

export interface ProblemPartAttempt {
  attemptId: string;
  problemId: string;
  partId: string;
  correct: boolean;
  /** A retry within a lesson's mistake round. */
  isLessonRetry?: boolean;
  lessonRunId?: string | null;
}

export type RecordOutcome =
  | { type: 'recorded'; xpAwarded: number; reviewCard: ReviewCard | null }
  | { type: 'duplicate' }
  | { type: 'rejected'; reason?: string };

export type LessonRunSettlementOutcome =
  | {
      type: 'settled';
      xpAwarded: number;
      baseXp: number;
      paceBonusXp: number;
      totalXp: number;
    }
  | { type: 'duplicate' }
  | { type: 'rejected'; reason?: string };

export interface SettleLessonRunOptions {
  activeDurationMs?: number;
  baselineDurationMs?: number;
  includePaceBonus?: boolean;
}

interface PlacedExercise {
  exercise: Exercise;
  lessonId: string;
  chapterId: string;
}

class LessonRunSession {
  readonly attempts: StagedLessonAttempt[] = [];
  readonly attemptIds = new Set<string>();
  settled: boolean = false;
  readonly lessonId: string;

  constructor(lessonId: string) {
    this.lessonId = lessonId;
  }
}

export interface Clock {
  now(): number;
}

export class SystemClock implements Clock {
  now(): number {
    return Date.now();
  }
}

/**
 * The single coordinator that manages progress mutations, lesson staging,
 * spaced repetition seeds, and atomic idempotent settlements.
 */
export class ProgressRecorder {
  private readonly streaks: StreakCalculator = new DefaultStreakCalculator();
  private readonly lessonRuns = new Map<string, LessonRunSession>();
  private readonly progressRepository: ProgressRepository;
  private readonly courseRepository: CourseRepository;
  private readonly settingsRepository: SettingsRepository;
  private readonly reviewScheduler: ReviewScheduler;
  private readonly xpPolicy: XpPolicy;
  private readonly clock: Clock;
  private readonly timeZone: string;

  constructor(
    progressRepository: ProgressRepository,
    courseRepository: CourseRepository,
    settingsRepository: SettingsRepository,
    reviewScheduler: ReviewScheduler = new LeitnerScheduler(),
    xpPolicy: XpPolicy = new DefaultXpPolicy(),
    clock: Clock = new SystemClock(),
    timeZone: string = 'UTC'
  ) {
    this.progressRepository = progressRepository;
    this.courseRepository = courseRepository;
    this.settingsRepository = settingsRepository;
    this.reviewScheduler = reviewScheduler;
    this.xpPolicy = xpPolicy;
    this.clock = clock;
    this.timeZone = timeZone;
  }

  now(): number {
    return this.clock.now();
  }

  today(): number {
    return effectiveEpochDay(this.clock.now(), this.timeZone);
  }

  /**
   * Opens a staged lesson run. Returns true if created or active.
   */
  async beginLessonRun(lessonId: string, runId: string): Promise<boolean> {
    if (!lessonId.trim() || !runId.trim()) return false;

    const existing = this.lessonRuns.get(runId);
    if (existing) {
      return existing.lessonId === lessonId && !existing.settled;
    }

    const courses = await this.courseRepository.listCourses();
    const found = courses.some((c) =>
      c.chapters.some((ch) => ch.lessons.some((l) => l.id === lessonId))
    );
    if (!found) {
      return false;
    }

    // Clean up finished runs
    for (const [key, session] of this.lessonRuns.entries()) {
      if (session.settled) {
        this.lessonRuns.delete(key);
      }
    }

    this.lessonRuns.set(runId, new LessonRunSession(lessonId));
    return true;
  }

  /**
   * Discards an unfinished run.
   */
  abandonLessonRun(runId: string): void {
    this.lessonRuns.delete(runId);
  }

  /**
   * Provisional XP shown by the lesson player while the run remains active.
   */
  lessonRunXp(runId: string): number {
    const session = this.lessonRuns.get(runId);
    if (!session) return 0;
    return session.attempts.reduce((sum, a) => sum + a.result.xpAwarded, 0);
  }

  /**
   * Checks whether a passing result was recorded in the active run.
   */
  lessonRunHasPassed(runId: string, exerciseId: string): boolean {
    const session = this.lessonRuns.get(runId);
    if (!session || session.settled) return false;
    return session.attempts.some(
      (a) => a.result.exerciseId === exerciseId && a.result.correct
    );
  }

  /**
   * Records a graded exercise attempt or problem part attempt.
   */
  async record(
    attempt: ExerciseAttempt | ProblemPartAttempt
  ): Promise<RecordOutcome> {
    if ('partId' in attempt) {
      return this.recordProblemPart(attempt);
    }
    return this.recordExercise(attempt);
  }

  /**
   * Atomically and idempotently settles a completed lesson run.
   */
  async settleLessonRun(
    lessonId: string,
    runId: string,
    options?: SettleLessonRunOptions
  ): Promise<LessonRunSettlementOutcome> {
    const session = this.lessonRuns.get(runId);
    if (!session || session.lessonId !== lessonId) {
      return { type: 'rejected', reason: 'Lesson run not found or mismatch' };
    }
    if (session.settled) {
      return { type: 'duplicate' };
    }

    const courses = await this.courseRepository.listCourses();
    let targetLesson = null;
    for (const c of courses) {
      for (const ch of c.chapters) {
        for (const l of ch.lessons) {
          if (l.id === lessonId) {
            targetLesson = l;
            break;
          }
        }
      }
    }

    if (!targetLesson) {
      return { type: 'rejected', reason: 'Lesson not found in curriculum' };
    }

    // A new run may be opened for practice, but a completed lesson must not
    // mint its completion XP a second time. The caller can still inspect the
    // lesson and use the separate review flow for additional practice.
    if (await this.progressRepository.isLessonCompleted(lessonId)) {
      session.settled = true;
      return { type: 'duplicate' };
    }

    const requiredExerciseIds = new Set(
      targetLesson.exercises.flatMap((exercise) =>
        exercise.type === 'multi_part_problem'
          ? exercise.parts.map((part) => `${exercise.id}:${part.id}`)
          : [exercise.id]
      )
    );
    const passedExerciseIds = new Set(
      session.attempts
        .filter((a) => a.result.correct && a.result.lessonId === lessonId)
        .map((a) => a.result.problemId ? `${a.result.problemId}:${a.result.exerciseId}` : a.result.exerciseId)
    );

    const hasForeignAttempts = session.attempts.some(
      (a) => a.result.lessonId !== lessonId
    );
    const allRequiredPassed = Array.from(requiredExerciseIds).every((id) =>
      passedExerciseIds.has(id)
    );

    if (hasForeignAttempts || !allRequiredPassed) {
      return { type: 'rejected', reason: 'Not all exercises completed' };
    }

    const nowMillis = this.clock.now();
    const dayEpoch = effectiveEpochDay(nowMillis, this.timeZone);
    const baseXp = session.attempts.reduce((sum, a) => sum + a.result.xpAwarded, 0);

    // Calculate pace bonus
    let paceBonusXp = 0;
    if (options?.includePaceBonus !== false && options?.activeDurationMs != null) {
      const baselineMs =
        options.baselineDurationMs ??
        this.xpPolicy.estimateLessonBaselineMs(targetLesson);

      const hintsCount = session.attempts.reduce(
        (acc, a) => acc + (a.result.xpAwarded === 0 ? 0 : 0),
        0
      );

      const bonusResult = this.xpPolicy.calculatePaceBonus({
        baseXp,
        activeDurationMs: options.activeDurationMs,
        baselineDurationMs: baselineMs,
        hintsUsed: hintsCount,
        allCorrect: allRequiredPassed,
      });
      paceBonusXp = bonusResult.bonusXp;
    }

    const totalXp = baseXp + paceBonusXp;

    const priorActivity = await this.progressRepository.getDailyActivity(dayEpoch);
    const dailyGoalTarget = await this.settingsRepository.getDailyGoalXp();
    const exercisesCompletedCount = session.attempts.filter((a) => a.completesExercise).length;

    let dailyActivity: DailyActivity | null = null;
    if (session.attempts.length > 0 || totalXp > 0) {
      const xpToday = (priorActivity?.xpEarned ?? 0) + totalXp;
      dailyActivity = {
        epochDay: dayEpoch,
        xpEarned: xpToday,
        exercisesCompleted: (priorActivity?.exercisesCompleted ?? 0) + exercisesCompletedCount,
        goalMet: xpToday >= dailyGoalTarget,
      };
    }

    const allActivities = await this.progressRepository.getDailyActivities();
    const history = dailyActivity
      ? [...allActivities.filter((a) => a.epochDay !== dayEpoch), dailyActivity]
      : allActivities;

    const streakState = this.evaluateStreak(history, dayEpoch);

    // Seeded review cards from mistakes are scheduled relative to the settlement day
    const reviewCards: ReviewCard[] = [];
    const seenCardIds = new Set<string>();
    for (const attempt of session.attempts) {
      if (attempt.reviewCard && !seenCardIds.has(attempt.reviewCard.itemId)) {
        seenCardIds.add(attempt.reviewCard.itemId);
        const attemptedDay = effectiveEpochDay(attempt.result.epochMillis, this.timeZone);
        const interval = Math.max(1, attempt.reviewCard.dueEpochDay - attemptedDay);
        reviewCards.push({
          ...attempt.reviewCard,
          dueEpochDay: dayEpoch + interval,
        });
      }
    }

    const settlement: LessonProgressSettlement = {
      runId,
      lessonId,
      completedAtEpochMillis: nowMillis,
      attempts: [...session.attempts],
      dailyActivity,
      reviewCards,
      frozenEpochDays: streakState.frozenDates.map(toEpochDay),
      heldStreakFreezes: streakState.heldFreezes,
      baseXp,
      paceBonusXp,
      totalXp,
    };

    const committed = await this.progressRepository.settleLessonProgress(settlement);
    session.settled = true;

    if (!committed) {
      return { type: 'duplicate' };
    }

    return {
      type: 'settled',
      xpAwarded: totalXp,
      baseXp,
      paceBonusXp,
      totalXp,
    };
  }

  /**
   * Refreshes the streak evaluation as of today and commits spent freezes.
   */
  async refreshStreak(): Promise<StreakFreezeState> {
    const todayEpoch = effectiveEpochDay(this.clock.now(), this.timeZone);
    const history = await this.progressRepository.getDailyActivities();
    const state = this.evaluateStreak(history, todayEpoch);

    const storedFreezes = new Set(await this.progressRepository.getFrozenEpochDays());
    for (const dateStr of state.frozenDates) {
      const epoch = toEpochDay(dateStr);
      if (!storedFreezes.has(epoch)) {
        await this.progressRepository.recordStreakFreeze(epoch);
      }
    }

    const currentHeld = await this.progressRepository.getHeldStreakFreezes();
    if (currentHeld !== state.heldFreezes) {
      await this.progressRepository.setHeldStreakFreezes(state.heldFreezes);
    }

    return state;
  }

  /** Legacy completion hook. */
  async completeLesson(lessonId: string): Promise<void> {
    await this.progressRepository.markLessonCompleted(lessonId, this.clock.now());
  }

  private async recordExercise(attempt: ExerciseAttempt): Promise<RecordOutcome> {
    const placedMap = await this.placedExercises();
    const placed = placedMap.get(attempt.exerciseId);
    if (!placed) {
      return { type: 'rejected', reason: 'Exercise not found' };
    }

    const session = (attempt.lessonRunId ? this.lessonRuns.get(attempt.lessonRunId) : null) ?? null;
    if (
      attempt.lessonRunId != null &&
      (!session || session.lessonId !== placed.lessonId || session.settled)
    ) {
      return { type: 'rejected', reason: 'Invalid or settled lesson run' };
    }

    if (session?.attemptIds.has(attempt.attemptId)) {
      return { type: 'duplicate' };
    }
    if (session && attempt.review) {
      return { type: 'rejected', reason: 'Review in lesson run' };
    }

    const exercise = placed.exercise;
    const nowMillis = this.clock.now();
    const dayEpoch = effectiveEpochDay(nowMillis, this.timeZone);
    const prior = await this.exerciseHistory(exercise.id, session);
    const alreadyPassed = prior.some((r) => r.correct);

    const isCard =
      exercise.type === 'multiple_choice' ||
      exercise.type === 'cloze' ||
      exercise.type === 'predict_output';

    if (isCard && attempt.review) {
      return this.recordReview(attempt, placed, nowMillis, dayEpoch);
    }

    if (isCard) {
      return this.recordLessonCard(attempt, placed, nowMillis, dayEpoch, session);
    }

    if (exercise.type === 'write_function' && !attempt.review) {
      const firstPass = attempt.correct && !alreadyPassed;
      const baseXp = firstPass
        ? this.xpPolicy.xpForExercise(exercise, prior.length === 0, true)
        : 0;

      const hints = attempt.aiHintsUsed ?? [];
      const xp = firstPass
        ? hints.reduce((acc, lvl) => this.xpPolicy.xpAfterHint(acc, lvl), baseXp)
        : 0;

      return this.acceptAttempt(
        session,
        attempt.attemptId,
        this.createResult(placed, exercise.id, attempt.correct, xp, nowMillis),
        firstPass,
        null,
        dayEpoch
      );
    }

    if (exercise.type === 'multi_part_problem' && attempt.correct && !attempt.review) {
      const multiPart = exercise as MultiPartProblemExercise;
      const allPartsPassed = await this.allProblemPartsPassed(multiPart, session);
      if (!allPartsPassed) {
        return { type: 'rejected', reason: 'Complete every problem part first' };
      }
      return this.acceptAttempt(
        session,
        attempt.attemptId,
        this.createResult(placed, exercise.id, true, 0, nowMillis),
        !alreadyPassed && !allPartsPassed,
        null,
        dayEpoch
      );
    }

    return { type: 'rejected', reason: 'Unsupported exercise attempt' };
  }

  private async recordLessonCard(
    attempt: ExerciseAttempt,
    placed: PlacedExercise,
    nowMillis: number,
    dayEpoch: number,
    session: LessonRunSession | null
  ): Promise<RecordOutcome> {
    const exercise = placed.exercise;
    const xp = attempt.correct
      ? this.xpPolicy.xpForExercise(exercise, true, true)
      : 0;

    const existingCard = await this.progressRepository.getReviewCard(exercise.id);
    const stagedCard = session?.attempts
      .slice()
      .reverse()
      .find((a) => a.reviewCard?.itemId === exercise.id)?.reviewCard;

    const seedCard =
      !attempt.correct && !existingCard && !stagedCard
        ? this.reviewScheduler.newCard(exercise.id, dayEpoch)
        : null;

    return this.acceptAttempt(
      session,
      attempt.attemptId,
      this.createResult(placed, exercise.id, attempt.correct, xp, nowMillis),
      attempt.correct,
      seedCard,
      dayEpoch
    );
  }

  private async recordReview(
    attempt: ExerciseAttempt,
    placed: PlacedExercise,
    nowMillis: number,
    dayEpoch: number
  ): Promise<RecordOutcome> {
    const exercise = placed.exercise;
    const existing = await this.progressRepository.getReviewCard(exercise.id);
    const card = existing ?? this.reviewScheduler.newCard(exercise.id, dayEpoch);
    const rescheduled = this.reviewScheduler.scheduleNextReview(card, attempt.correct, dayEpoch);
    const xp = attempt.correct ? this.xpPolicy.xpForExercise(exercise, true, true) : 0;

    return this.acceptAttempt(
      null,
      attempt.attemptId,
      this.createResult(placed, exercise.id, attempt.correct, xp, nowMillis),
      true,
      rescheduled,
      dayEpoch
    );
  }

  private async recordProblemPart(attempt: ProblemPartAttempt): Promise<RecordOutcome> {
    const placedMap = await this.placedExercises();
    const problem = placedMap.get(attempt.problemId);
    if (!problem || problem.exercise.type !== 'multi_part_problem') {
      return { type: 'rejected', reason: 'Problem not found' };
    }

    const multiPart = problem.exercise as MultiPartProblemExercise;
    if (!multiPart.parts.some((p) => p.id === attempt.partId)) {
      return { type: 'rejected', reason: 'Part not in problem' };
    }

    const session = (attempt.lessonRunId ? this.lessonRuns.get(attempt.lessonRunId) : null) ?? null;
    if (
      attempt.lessonRunId != null &&
      (!session || session.lessonId !== problem.lessonId || session.settled)
    ) {
      return { type: 'rejected', reason: 'Invalid or settled run' };
    }
    if (session?.attemptIds.has(attempt.attemptId)) {
      return { type: 'duplicate' };
    }

    const nowMillis = this.clock.now();
    const dayEpoch = effectiveEpochDay(nowMillis, this.timeZone);
    const prior = await this.exerciseHistory(attempt.partId, session);
    const xp =
      attempt.correct && !prior.some((r) => r.correct)
        ? this.xpPolicy.xpForMultiPartPart(true)
        : 0;

    const allOtherPartsPassed = (
      await Promise.all(
        multiPart.parts
          .filter((part) => part.id !== attempt.partId)
          .map(async (part) => {
            const history = await this.exerciseHistory(part.id, session);
            return history.some((result) => result.correct);
          })
      )
    ).every(Boolean);
    const allPartsPassed = attempt.correct && allOtherPartsPassed;

    return this.acceptAttempt(
      session,
      attempt.attemptId,
      this.createResult(problem, attempt.partId, attempt.correct, xp, nowMillis, attempt.problemId),
      allPartsPassed,
      null,
      dayEpoch
    );
  }

  private async acceptAttempt(
    session: LessonRunSession | null,
    attemptId: string,
    result: ExerciseResult,
    completesExercise: boolean,
    reviewCard: ReviewCard | null,
    dayEpoch: number
  ): Promise<RecordOutcome> {
    if (session) {
      if (session.attemptIds.has(attemptId)) {
        return { type: 'duplicate' };
      }
      session.attemptIds.add(attemptId);
      session.attempts.push({
        attemptId,
        result,
        completesExercise,
        reviewCard,
      });
      return { type: 'recorded', xpAwarded: result.xpAwarded, reviewCard };
    }

    return this.commitStandalone(attemptId, result, completesExercise, reviewCard, dayEpoch);
  }

  private async commitStandalone(
    attemptId: string,
    result: ExerciseResult,
    completesExercise: boolean,
    reviewCard: ReviewCard | null,
    dayEpoch: number
  ): Promise<RecordOutcome> {
    const goalTarget = await this.settingsRepository.getDailyGoalXp();
    const existingActivity = await this.progressRepository.getDailyActivity(dayEpoch);
    const xpToday = (existingActivity?.xpEarned ?? 0) + result.xpAwarded;

    const activity: DailyActivity = {
      epochDay: dayEpoch,
      xpEarned: xpToday,
      exercisesCompleted:
        (existingActivity?.exercisesCompleted ?? 0) + (completesExercise ? 1 : 0),
      goalMet: xpToday >= goalTarget,
    };

    const allActivities = await this.progressRepository.getDailyActivities();
    const history = [
      ...allActivities.filter((a) => a.epochDay !== dayEpoch),
      activity,
    ];
    const streak = this.evaluateStreak(history, dayEpoch);

    const commit: ProgressCommit = {
      attemptId,
      result,
      dailyActivity: activity,
      reviewCard,
      frozenEpochDays: streak.frozenDates.map(toEpochDay),
      heldStreakFreezes: streak.heldFreezes,
    };

    const committed = await this.progressRepository.commitProgress(commit);
    if (!committed) {
      return { type: 'duplicate' };
    }
    return { type: 'recorded', xpAwarded: result.xpAwarded, reviewCard };
  }

  private evaluateStreak(history: DailyActivity[], dayEpoch: number): StreakFreezeState {
    const todayDateStr = fromEpochDay(dayEpoch);
    const goalDays = history
      .filter((a) => a.goalMet)
      .map((a) => fromEpochDay(a.epochDay));
    return this.streaks.evaluate(goalDays, todayDateStr);
  }

  private async exerciseHistory(
    exerciseId: string,
    session: LessonRunSession | null
  ): Promise<ExerciseResult[]> {
    const persisted = await this.progressRepository.getExerciseResultsForExercise(exerciseId);
    const staged =
      session?.attempts
        .filter((a) => a.result.exerciseId === exerciseId)
        .map((a) => a.result) ?? [];
    return [...persisted, ...staged];
  }

  private createResult(
    placed: PlacedExercise,
    exerciseId: string,
    correct: boolean,
    xpAwarded: number,
    epochMillis: number,
    problemId?: string | null
  ): ExerciseResult {
    return {
      exerciseId,
      problemId: problemId ?? null,
      correct,
      xpAwarded,
      epochMillis,
      lessonId: placed.lessonId,
      chapterId: placed.chapterId,
    };
  }

  private async allProblemPartsPassed(
    problem: MultiPartProblemExercise,
    session: LessonRunSession | null
  ): Promise<boolean> {
    const results = await Promise.all(
      problem.parts.map(async (part) => {
        const history = await this.exerciseHistory(part.id, session);
        return history.some((result) => result.correct);
      })
    );
    return results.every(Boolean);
  }

  private async placedExercises(): Promise<Map<string, PlacedExercise>> {
    const map = new Map<string, PlacedExercise>();
    const courses = await this.courseRepository.listCourses();
    for (const c of courses) {
      for (const ch of c.chapters) {
        for (const l of ch.lessons) {
          for (const ex of l.exercises) {
            map.set(ex.id, {
              exercise: ex,
              lessonId: l.id,
              chapterId: ch.id,
            });
          }
        }
      }
    }
    return map;
  }
}
