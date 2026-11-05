import type { Exercise } from '../models/exercise.ts';
import type { Lesson } from '../models/lesson.ts';

export const CARD_XP = 10;
export const CODE_FIRST_TRY_XP = 25;
export const CODE_RETRY_XP = 15;
export const MULTI_PART_XP = 50;

export const HINT_LEVEL_ONE_COST = 2;
export const HINT_LEVEL_TWO_COST = 5;
export const HINT_LEVEL_THREE_COST = 10;

/** Default maximum pace bonus as a fraction of base lesson XP (25%). */
export const DEFAULT_MAX_PACE_BONUS_RATIO = 0.25;

export interface PaceBonusParams {
  /** Total base XP earned in the lesson (must be positive). */
  baseXp: number;
  /** Active learning duration in milliseconds (pauses/backgrounding excluded). */
  activeDurationMs: number;
  /** Baseline or authored expected duration in milliseconds. */
  baselineDurationMs: number;
  /** Hint levels used (e.g. [1, 2]) or total count of hints used. */
  hintsUsed?: number[] | number;
  /** Whether the lesson was completed correctly with all required exercises passed. */
  allCorrect?: boolean;
  /** Maximum pace bonus ratio cap (default: 0.25 = 25%). */
  maxBonusRatio?: number;
}

export interface PaceBonusResult {
  /** Awarded pace bonus XP (non-negative integer). */
  bonusXp: number;
  /** Maximum possible bonus XP given baseXp and maxBonusRatio. */
  maxPossibleBonusXp: number;
  /** Raw speed advantage ratio (timeSaved / baselineDuration). */
  rawAdvantageRatio: number;
  /** Effective bonus ratio after clamping to cap. */
  effectiveRatio: number;
  /** Total hint deduction applied to the bonus. */
  hintDeductionXp: number;
  /** Whether the learner qualified for a pace bonus. */
  eligible: boolean;
  /** Descriptive reason for outcome. */
  reason: string;
}

/**
 * Calculates XP rewards for exercises, hints, multi-part problems, and pace bonuses.
 */
export interface XpPolicy {
  xpForExercise(exercise: Exercise, isFirstAttempt: boolean, correct?: boolean): number;
  xpForMultiPartPart(correct: boolean): number;
  hintCost(level: number): number;
  xpAfterHint(currentExerciseXp: number, level: number): number;
  calculatePaceBonus(params: PaceBonusParams): PaceBonusResult;
  estimateLessonBaselineMs(lesson: Lesson): number;
}

export class DefaultXpPolicy implements XpPolicy {
  private readonly maxBonusRatio: number;

  constructor(maxBonusRatio: number = DEFAULT_MAX_PACE_BONUS_RATIO) {
    if (maxBonusRatio < 0 || maxBonusRatio > 1) {
      throw new Error('maxBonusRatio must be between 0.0 and 1.0');
    }
    this.maxBonusRatio = maxBonusRatio;
  }

  /**
   * Determines XP awarded for completing an exercise attempt.
   */
  xpForExercise(
    exercise: Exercise,
    isFirstAttempt: boolean,
    correct: boolean = true
  ): number {
    if (!correct) return 0;
    switch (exercise.type) {
      case 'multiple_choice':
      case 'cloze':
      case 'predict_output':
        return CARD_XP;
      case 'write_function':
        return isFirstAttempt ? CODE_FIRST_TRY_XP : CODE_RETRY_XP;
      case 'multi_part_problem':
        return MULTI_PART_XP;
      default:
        return 0;
    }
  }

  /** XP awarded for one successfully completed part of a multi-part problem. */
  xpForMultiPartPart(correct: boolean): number {
    return correct ? MULTI_PART_XP : 0;
  }

  /** XP cost for an AI hint level (1 = nudge, 2 = approach, 3 = near-solution). */
  hintCost(level: number): number {
    switch (level) {
      case 1:
        return HINT_LEVEL_ONE_COST;
      case 2:
        return HINT_LEVEL_TWO_COST;
      case 3:
        return HINT_LEVEL_THREE_COST;
      default:
        throw new Error('Hint level must be between 1 and 3');
    }
  }

  /** Deducts a hint cost without allowing the exercise XP balance to drop below zero. */
  xpAfterHint(currentExerciseXp: number, level: number): number {
    return Math.max(0, currentExerciseXp - this.hintCost(level));
  }

  /**
   * Calculates a capped pace bonus for completing a lesson faster than its baseline.
   *
   * Rules:
   * - Requires a complete and correct lesson (`allCorrect !== false`).
   * - Requires activeDurationMs < baselineDurationMs and baseXp > 0.
   * - Capped at `maxBonusRatio` (default 25%) of base XP.
   * - Hints reduce or remove the pace bonus but never penalize base XP below zero.
   */
  calculatePaceBonus(params: PaceBonusParams): PaceBonusResult {
    const {
      baseXp,
      activeDurationMs,
      baselineDurationMs,
      hintsUsed = [],
      allCorrect = true,
      maxBonusRatio = this.maxBonusRatio,
    } = params;

    const maxPossibleBonusXp = Math.max(0, Math.round(baseXp * maxBonusRatio));

    if (!allCorrect) {
      return {
        bonusXp: 0,
        maxPossibleBonusXp,
        rawAdvantageRatio: 0,
        effectiveRatio: 0,
        hintDeductionXp: 0,
        eligible: false,
        reason: 'Lesson completed with errors or incomplete',
      };
    }

    if (baseXp <= 0) {
      return {
        bonusXp: 0,
        maxPossibleBonusXp: 0,
        rawAdvantageRatio: 0,
        effectiveRatio: 0,
        hintDeductionXp: 0,
        eligible: false,
        reason: 'No base XP earned',
      };
    }

    if (baselineDurationMs <= 0) {
      return {
        bonusXp: 0,
        maxPossibleBonusXp,
        rawAdvantageRatio: 0,
        effectiveRatio: 0,
        hintDeductionXp: 0,
        eligible: false,
        reason: 'Invalid baseline duration',
      };
    }

    if (activeDurationMs >= baselineDurationMs) {
      return {
        bonusXp: 0,
        maxPossibleBonusXp,
        rawAdvantageRatio: 0,
        effectiveRatio: 0,
        hintDeductionXp: 0,
        eligible: false,
        reason: 'Active duration met or exceeded baseline',
      };
    }

    const timeSavedMs = Math.max(0, baselineDurationMs - activeDurationMs);
    const rawAdvantageRatio = timeSavedMs / baselineDurationMs;
    const effectiveRatio = Math.min(maxBonusRatio, rawAdvantageRatio);
    const rawBonusXp = Math.round(baseXp * effectiveRatio);

    // Calculate hint deductions
    let hintDeductionXp = 0;
    if (Array.isArray(hintsUsed)) {
      hintDeductionXp = hintsUsed.reduce((acc, level) => acc + this.hintCost(level), 0);
    } else if (typeof hintsUsed === 'number' && hintsUsed > 0) {
      hintDeductionXp = hintsUsed * HINT_LEVEL_TWO_COST;
    }

    const bonusXp = Math.max(0, Math.min(maxPossibleBonusXp, rawBonusXp - hintDeductionXp));

    return {
      bonusXp,
      maxPossibleBonusXp,
      rawAdvantageRatio,
      effectiveRatio,
      hintDeductionXp,
      eligible: bonusXp > 0,
      reason:
        bonusXp > 0
          ? `Completed ${Math.round(timeSavedMs / 1000)}s faster than baseline`
          : 'Hint deductions reduced pace bonus to zero',
    };
  }

  /**
   * Estimates an authored baseline duration for a lesson based on its exercise composition.
   */
  estimateLessonBaselineMs(lesson: Lesson): number {
    let totalMs = 0;
    for (const ex of lesson.exercises) {
      switch (ex.type) {
        case 'multiple_choice':
        case 'cloze':
        case 'predict_output':
          totalMs += 30_000; // 30 seconds
          break;
        case 'write_function':
          totalMs += 120_000; // 2 minutes
          break;
        case 'multi_part_problem':
          totalMs += (ex.parts?.length || 2) * 90_000; // 1.5 min per part
          break;
        default:
          totalMs += 45_000;
      }
    }
    return Math.max(30_000, totalMs);
  }
}
