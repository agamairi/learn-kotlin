import type { DailyGoalProgress, ExerciseResult } from '../models/progress.ts';
import { effectiveLocalDate } from './date-time.ts';

export const DEFAULT_TARGET_XP = 50;

/**
 * Tracks daily XP progress towards learner-defined daily targets.
 */
export interface DailyGoalTracker {
  isGoalMet(xpToday: number, targetXp: number): boolean;
  progressRatio(xpToday: number, targetXp: number): number;
  xpToday(
    results: ExerciseResult[],
    now: number | Date | string,
    timeZone: string
  ): number;
  progress(
    results: ExerciseResult[],
    now: number | Date | string,
    timeZone: string,
    targetXp?: number
  ): DailyGoalProgress;
}

export class DefaultDailyGoalTracker implements DailyGoalTracker {
  readonly defaultTargetXp: number;

  constructor(defaultTargetXp: number = DEFAULT_TARGET_XP) {
    if (defaultTargetXp < 0) {
      throw new Error('The daily XP target cannot be negative');
    }
    this.defaultTargetXp = defaultTargetXp;
  }

  isGoalMet(xpToday: number, targetXp: number): boolean {
    return xpToday >= targetXp;
  }

  progressRatio(xpToday: number, targetXp: number): number {
    if (targetXp <= 0) return 1;
    return Math.max(0, Math.min(1, xpToday / targetXp));
  }

  /**
   * Sums exercise XP whose effective local date is today's effective date in `timeZone`.
   */
  xpToday(
    results: ExerciseResult[],
    now: number | Date | string,
    timeZone: string
  ): number {
    const nowDate = typeof now === 'number' || typeof now === 'string' ? new Date(now) : now;
    const nowMillis = nowDate.getTime();
    const today = effectiveLocalDate(nowDate, timeZone);

    let sum = 0;
    for (const r of results) {
      if (r.epochMillis <= nowMillis) {
        if (effectiveLocalDate(r.epochMillis, timeZone) === today) {
          sum += Math.max(0, r.xpAwarded);
        }
      }
    }
    return sum;
  }

  calculateXpToday(
    results: ExerciseResult[],
    now: number | Date | string,
    timeZone: string
  ): number {
    return this.xpToday(results, now, timeZone);
  }

  progress(
    results: ExerciseResult[],
    now: number | Date | string,
    timeZone: string,
    targetXp: number = this.defaultTargetXp
  ): DailyGoalProgress {
    if (targetXp < 0) {
      throw new Error('The daily XP target cannot be negative');
    }
    const xp = this.xpToday(results, now, timeZone);
    return {
      xpToday: xp,
      targetXp,
      isMet: this.isGoalMet(xp, targetXp),
      ratio: this.progressRatio(xp, targetXp),
    };
  }
}
