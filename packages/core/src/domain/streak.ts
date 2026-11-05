import type { StreakFreezeState, StreakSummary } from '../models/progress.ts';
import {
  effectiveLocalDate,
  nextDay,
  previousDay,
} from './date-time.ts';

export const FREEZE_EVERY_GOAL_DAYS = 7;
export const MAX_HELD_FREEZES = 2;

export interface StreakCalculator {
  calculateStreak(
    activityTimestamps: (number | Date | string)[],
    now: number | Date | string,
    timeZone: string
  ): number;

  calculateSummary(
    activityTimestamps: (number | Date | string)[],
    now: number | Date | string,
    timeZone: string
  ): StreakSummary;

  calculateStreakState(
    activityTimestamps: (number | Date | string)[],
    now: number | Date | string,
    timeZone: string
  ): StreakFreezeState;

  evaluate(
    goalDays: Iterable<string>,
    today: string
  ): StreakFreezeState;
}

export class DefaultStreakCalculator implements StreakCalculator {
  calculateStreak(
    activityTimestamps: (number | Date | string)[],
    now: number | Date | string,
    timeZone: string
  ): number {
    return this.calculateStreakState(activityTimestamps, now, timeZone).currentStreak;
  }

  calculateSummary(
    activityTimestamps: (number | Date | string)[],
    now: number | Date | string,
    timeZone: string
  ): StreakSummary {
    const state = this.calculateStreakState(activityTimestamps, now, timeZone);
    return {
      currentStreak: state.currentStreak,
      longestStreak: state.longestStreak,
    };
  }

  calculateStreakState(
    activityTimestamps: (number | Date | string)[],
    now: number | Date | string,
    timeZone: string
  ): StreakFreezeState {
    const nowDate = typeof now === 'number' || typeof now === 'string' ? new Date(now) : now;
    const nowMillis = nowDate.getTime();
    const today = effectiveLocalDate(nowDate, timeZone);

    const goalDays = new Set<string>();
    for (const ts of activityTimestamps) {
      const d = typeof ts === 'number' || typeof ts === 'string' ? new Date(ts) : ts;
      if (d.getTime() <= nowMillis) {
        const effDate = effectiveLocalDate(d, timeZone);
        if (effDate <= today) {
          goalDays.add(effDate);
        }
      }
    }

    return this.evaluate(goalDays, today);
  }

  /**
   * Evaluates streaks and freezes from the days on which the goal was met, as of `today`.
   * Days after `today` are ignored. Deterministic and pure.
   */
  evaluate(
    goalDays: Iterable<string>,
    today: string
  ): StreakFreezeState {
    const sortedGoals = Array.from(goalDays)
      .filter((day) => day <= today)
      .sort();

    if (sortedGoals.length === 0) {
      return {
        heldFreezes: 0,
        frozenDates: [],
        currentStreak: 0,
        longestStreak: 0,
      };
    }

    const goalSet = new Set(sortedGoals);
    const frozen = new Set<string>();
    let held = 0;
    let consecutiveGoalDays = 0;
    let day = sortedGoals[0];

    while (day <= today) {
      if (goalSet.has(day)) {
        consecutiveGoalDays++;
        if (consecutiveGoalDays % FREEZE_EVERY_GOAL_DAYS === 0) {
          held = Math.min(MAX_HELD_FREEZES, held + 1);
        }
      } else {
        // A missed day breaks the run of consecutive goal days, covered or not.
        consecutiveGoalDays = 0;
        // Today is not missed yet: the learner can still complete it.
        if (day < today && held > 0) {
          held--;
          frozen.add(day);
        }
      }
      day = nextDay(day);
    }

    const counted = new Set([...goalSet, ...frozen]);
    const currentStart = counted.has(today) ? today : previousDay(today);
    let current = 0;
    let cursor = currentStart;
    while (counted.has(cursor)) {
      current++;
      cursor = previousDay(cursor);
    }

    let longest = 0;
    let run = 0;
    day = sortedGoals[0];
    while (day <= today) {
      if (counted.has(day)) {
        run++;
        longest = Math.max(longest, run);
      } else {
        run = 0;
      }
      day = nextDay(day);
    }

    return {
      heldFreezes: held,
      frozenDates: Array.from(frozen).sort(),
      currentStreak: current,
      longestStreak: longest,
    };
  }
}
