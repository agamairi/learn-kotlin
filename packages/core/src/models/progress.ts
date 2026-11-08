/**
 * Result of an individual attempt on an exercise.
 */
export interface ExerciseResult {
  exerciseId: string;
  /** Parent exercise ID for a multipart part result. */
  problemId?: string | null;
  correct: boolean;
  xpAwarded: number;
  epochMillis: number;
  lessonId?: string | null;
  chapterId?: string | null;
}

/**
 * Aggregated daily activity record for tracking streak and daily goals.
 */
export interface DailyActivity {
  epochDay: number;
  xpEarned: number;
  exercisesCompleted: number;
  goalMet: boolean;
}

/**
 * A flashcard in the 5-box Leitner spaced repetition queue.
 */
export interface ReviewCard {
  itemId: string;
  box: number;
  dueEpochDay: number;
}

/**
 * Two streak values calculated from a learner's goal days.
 */
export interface StreakSummary {
  currentStreak: number;
  longestStreak: number;
}

/**
 * Streak freeze state evaluated for a learner's history.
 */
export interface StreakFreezeState {
  heldFreezes: number;
  frozenDates: string[];
  currentStreak: number;
  longestStreak: number;
}

/**
 * Snapshot of XP progress against a daily target.
 */
export interface DailyGoalProgress {
  xpToday: number;
  targetXp: number;
  isMet: boolean;
  ratio: number;
}
