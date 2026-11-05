import type { Course } from '../models/course.ts';
import type { DailyActivity, ExerciseResult, ReviewCard } from '../models/progress.ts';

/**
 * All durable changes for one standalone non-lesson attempt.
 */
export interface ProgressCommit {
  attemptId: string;
  result: ExerciseResult;
  dailyActivity: DailyActivity;
  reviewCard?: ReviewCard | null;
  frozenEpochDays?: number[];
  heldStreakFreezes?: number | null;
}

/**
 * One attempt held in memory until the whole lesson finishes.
 */
export interface StagedLessonAttempt {
  attemptId: string;
  result: ExerciseResult;
  completesExercise: boolean;
  reviewCard?: ReviewCard | null;
}

/**
 * All durable changes produced by one completed lesson run.
 */
export interface LessonProgressSettlement {
  runId: string;
  lessonId: string;
  completedAtEpochMillis: number;
  attempts: StagedLessonAttempt[];
  dailyActivity: DailyActivity | null;
  reviewCards: ReviewCard[];
  frozenEpochDays: number[];
  heldStreakFreezes: number;
  baseXp: number;
  paceBonusXp: number;
  totalXp: number;
}

/**
 * Storage interface for progress tracking, spaced repetition, and lesson completions.
 */
export interface ProgressRepository {
  recordExerciseResult(result: ExerciseResult): Promise<void> | void;
  commitProgress(commit: ProgressCommit): Promise<boolean> | boolean;
  settleLessonProgress(settlement: LessonProgressSettlement): Promise<boolean> | boolean;
  getExerciseResults(): Promise<ExerciseResult[]> | ExerciseResult[];
  getExerciseResultsForExercise(exerciseId: string): Promise<ExerciseResult[]> | ExerciseResult[];
  getExerciseResultsForLesson(lessonId: string): Promise<ExerciseResult[]> | ExerciseResult[];
  getDailyActivities(): Promise<DailyActivity[]> | DailyActivity[];
  getDailyActivity(epochDay: number): Promise<DailyActivity | null> | DailyActivity | null;
  upsertReviewCard(card: ReviewCard): Promise<void> | void;
  getReviewCard(itemId: string): Promise<ReviewCard | null> | ReviewCard | null;
  getDueReviewCards(epochDay: number): Promise<ReviewCard[]> | ReviewCard[];
  markLessonCompleted(lessonId: string, completedAtEpochMillis: number): Promise<void> | void;
  isLessonCompleted(lessonId: string): Promise<boolean> | boolean;
  getCompletedLessonIds(): Promise<Set<string>> | Set<string>;
  getFrozenEpochDays(): Promise<number[]> | number[];
  recordStreakFreeze(epochDay: number): Promise<void> | void;
  getHeldStreakFreezes(): Promise<number> | number;
  setHeldStreakFreezes(count: number): Promise<void> | void;
}

export interface SettingsRepository {
  getDailyGoalXp(): Promise<number> | number;
  setDailyGoalXp(xp: number): Promise<void> | void;
}

export interface CourseRepository {
  getCourse(courseId: string): Promise<Course | null> | Course | null;
  listCourses(): Promise<Course[]> | Course[];
}

/**
 * Dependency-free in-memory implementation of ProgressRepository for tests and reference usage.
 * Strictly guarantees idempotency on attemptId and runId.
 */
export class InMemoryProgressRepository implements ProgressRepository {
  private readonly exerciseResults: ExerciseResult[] = [];
  private readonly committedAttemptIds = new Set<string>();
  private readonly settledRunIds = new Set<string>();
  private readonly dailyActivities = new Map<number, DailyActivity>();
  private readonly reviewCards = new Map<string, ReviewCard>();
  private readonly lessonCompletions = new Map<string, number>();
  private readonly frozenDays = new Set<number>();
  private heldFreezes: number = 0;

  recordExerciseResult(result: ExerciseResult): void {
    this.exerciseResults.push({ ...result });
  }

  commitProgress(commit: ProgressCommit): boolean {
    if (this.committedAttemptIds.has(commit.attemptId)) {
      return false;
    }
    this.committedAttemptIds.add(commit.attemptId);
    this.recordExerciseResult(commit.result);
    this.dailyActivities.set(commit.dailyActivity.epochDay, { ...commit.dailyActivity });

    if (commit.reviewCard) {
      this.reviewCards.set(commit.reviewCard.itemId, { ...commit.reviewCard });
    }
    if (commit.frozenEpochDays) {
      for (const d of commit.frozenEpochDays) {
        this.frozenDays.add(d);
      }
    }
    if (commit.heldStreakFreezes != null) {
      this.heldFreezes = Math.max(0, Math.min(2, commit.heldStreakFreezes));
    }
    return true;
  }

  settleLessonProgress(settlement: LessonProgressSettlement): boolean {
    if (this.settledRunIds.has(settlement.runId)) {
      return false;
    }
    this.settledRunIds.add(settlement.runId);

    for (const attempt of settlement.attempts) {
      this.committedAttemptIds.add(attempt.attemptId);
      this.recordExerciseResult(attempt.result);
    }

    if (settlement.dailyActivity) {
      this.dailyActivities.set(settlement.dailyActivity.epochDay, { ...settlement.dailyActivity });
    }

    for (const card of settlement.reviewCards) {
      // Review cards seeded by mistake round are inserted only if not already present
      if (!this.reviewCards.has(card.itemId)) {
        this.reviewCards.set(card.itemId, { ...card });
      }
    }

    for (const d of settlement.frozenEpochDays) {
      this.frozenDays.add(d);
    }
    this.heldFreezes = Math.max(0, Math.min(2, settlement.heldStreakFreezes));
    this.lessonCompletions.set(settlement.lessonId, settlement.completedAtEpochMillis);
    return true;
  }

  getExerciseResults(): ExerciseResult[] {
    return [...this.exerciseResults];
  }

  getExerciseResultsForExercise(exerciseId: string): ExerciseResult[] {
    return this.exerciseResults.filter((r) => r.exerciseId === exerciseId);
  }

  getExerciseResultsForLesson(lessonId: string): ExerciseResult[] {
    return this.exerciseResults.filter((r) => r.lessonId === lessonId);
  }

  getDailyActivities(): DailyActivity[] {
    return Array.from(this.dailyActivities.values()).sort((a, b) => a.epochDay - b.epochDay);
  }

  getDailyActivity(epochDay: number): DailyActivity | null {
    const act = this.dailyActivities.get(epochDay);
    return act ? { ...act } : null;
  }

  upsertReviewCard(card: ReviewCard): void {
    this.reviewCards.set(card.itemId, { ...card });
  }

  getReviewCard(itemId: string): ReviewCard | null {
    const card = this.reviewCards.get(itemId);
    return card ? { ...card } : null;
  }

  getDueReviewCards(epochDay: number): ReviewCard[] {
    return Array.from(this.reviewCards.values()).filter((c) => c.dueEpochDay <= epochDay);
  }

  markLessonCompleted(lessonId: string, completedAtEpochMillis: number): void {
    this.lessonCompletions.set(lessonId, completedAtEpochMillis);
  }

  isLessonCompleted(lessonId: string): boolean {
    return this.lessonCompletions.has(lessonId);
  }

  getCompletedLessonIds(): Set<string> {
    return new Set(this.lessonCompletions.keys());
  }

  getFrozenEpochDays(): number[] {
    return Array.from(this.frozenDays).sort((a, b) => a - b);
  }

  recordStreakFreeze(epochDay: number): void {
    this.frozenDays.add(epochDay);
  }

  getHeldStreakFreezes(): number {
    return this.heldFreezes;
  }

  setHeldStreakFreezes(count: number): void {
    this.heldFreezes = Math.max(0, Math.min(2, count));
  }
}

export class InMemorySettingsRepository implements SettingsRepository {
  private dailyGoalXp: number = 50;

  getDailyGoalXp(): number {
    return this.dailyGoalXp;
  }

  setDailyGoalXp(xp: number): void {
    if (xp < 0) throw new Error('Daily goal XP cannot be negative');
    this.dailyGoalXp = xp;
  }
}

export class StaticCourseRepository implements CourseRepository {
  private readonly courses: Course[];

  constructor(courses: Course[]) {
    this.courses = courses;
  }

  getCourse(courseId: string): Course | null {
    return this.courses.find((c) => c.id === courseId) ?? null;
  }

  listCourses(): Course[] {
    return [...this.courses];
  }
}
