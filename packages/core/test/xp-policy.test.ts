import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  DefaultXpPolicy,
  CARD_XP,
  CODE_FIRST_TRY_XP,
  CODE_RETRY_XP,
  MULTI_PART_XP,
  HINT_LEVEL_ONE_COST,
  HINT_LEVEL_TWO_COST,
  HINT_LEVEL_THREE_COST,
} from '../src/domain/xp-policy.ts';
import type {
  MultipleChoiceExercise,
  WriteFunctionExercise,
  MultiPartProblemExercise,
} from '../src/models/exercise.ts';
import type { Lesson } from '../src/models/lesson.ts';

describe('XpPolicy', () => {
  const policy = new DefaultXpPolicy();

  const multipleChoice: MultipleChoiceExercise = {
    type: 'multiple_choice',
    id: 'mc',
    prompt: 'Choose',
    options: ['A', 'B'],
    correctIndex: 0,
    explanation: '',
  };

  const writeFunction: WriteFunctionExercise = {
    type: 'write_function',
    id: 'code',
    prompt: 'Implement',
    starterCode: 'fun f() = 0',
    hiddenTests: '',
    referenceSolution: 'fun f() = 1',
  };

  const multiPart: MultiPartProblemExercise = {
    type: 'multi_part_problem',
    id: 'mp',
    prompt: 'Multi part',
    story: 'Story',
    parts: [
      {
        id: 'p1',
        prompt: 'Part 1',
        hiddenTests: '',
        referenceSolution: '',
        expected: '',
      },
    ],
  };

  describe('Base XP Calculations', () => {
    test('awards correct XP per exercise type and attempt', () => {
      assert.equal(policy.xpForExercise(multipleChoice, true), CARD_XP);
      assert.equal(policy.xpForExercise(writeFunction, true), CODE_FIRST_TRY_XP);
      assert.equal(policy.xpForExercise(writeFunction, false), CODE_RETRY_XP);
      assert.equal(policy.xpForExercise(multiPart, true), MULTI_PART_XP);
    });

    test('awards zero XP for incorrect attempts', () => {
      assert.equal(policy.xpForExercise(multipleChoice, true, false), 0);
      assert.equal(policy.xpForExercise(writeFunction, true, false), 0);
      assert.equal(policy.xpForMultiPartPart(false), 0);
      assert.equal(policy.xpForMultiPartPart(true), MULTI_PART_XP);
    });

    test('deducts hint costs capped at zero', () => {
      assert.equal(policy.hintCost(1), HINT_LEVEL_ONE_COST);
      assert.equal(policy.hintCost(2), HINT_LEVEL_TWO_COST);
      assert.equal(policy.hintCost(3), HINT_LEVEL_THREE_COST);
      assert.throws(() => policy.hintCost(4));

      assert.equal(policy.xpAfterHint(25, 1), 23);
      assert.equal(policy.xpAfterHint(5, 3), 0); // 5 - 10 clamped to 0
    });
  });

  describe('Capped Pace Bonus', () => {
    test('awards capped pace bonus when completed faster than baseline', () => {
      // 100 base XP, 60s active vs 120s baseline (50% time saved)
      const res = policy.calculatePaceBonus({
        baseXp: 100,
        activeDurationMs: 60_000,
        baselineDurationMs: 120_000,
        allCorrect: true,
      });

      assert.equal(res.eligible, true);
      assert.equal(res.maxPossibleBonusXp, 25);
      // Capped at 25% max bonus ratio
      assert.equal(res.bonusXp, 25);
      assert.equal(res.effectiveRatio, 0.25);
    });

    test('scales bonus proportionally when under the cap', () => {
      // 100 base XP, 108s active vs 120s baseline (10% time saved)
      const res = policy.calculatePaceBonus({
        baseXp: 100,
        activeDurationMs: 108_000,
        baselineDurationMs: 120_000,
        allCorrect: true,
      });

      assert.equal(res.eligible, true);
      assert.equal(res.bonusXp, 10);
      assert.equal(res.rawAdvantageRatio, 0.1);
    });

    test('awards zero bonus if duration exceeds or equals baseline', () => {
      const res = policy.calculatePaceBonus({
        baseXp: 100,
        activeDurationMs: 120_000,
        baselineDurationMs: 120_000,
        allCorrect: true,
      });

      assert.equal(res.eligible, false);
      assert.equal(res.bonusXp, 0);
    });

    test('requires full correctness and complete lesson', () => {
      const res = policy.calculatePaceBonus({
        baseXp: 100,
        activeDurationMs: 30_000,
        baselineDurationMs: 120_000,
        allCorrect: false,
      });

      assert.equal(res.eligible, false);
      assert.equal(res.bonusXp, 0);
    });

    test('hints reduce pace bonus without creating negative penalties', () => {
      // 100 base XP, 50% time saved -> 25 raw bonus
      // Hint level 3 costs 10 -> bonus reduces to 15
      const resWithHint = policy.calculatePaceBonus({
        baseXp: 100,
        activeDurationMs: 60_000,
        baselineDurationMs: 120_000,
        hintsUsed: [3],
        allCorrect: true,
      });

      assert.equal(resWithHint.bonusXp, 15);
      assert.equal(resWithHint.hintDeductionXp, 10);

      // Multiple hints exceed the bonus -> clamped at 0
      const resExceeding = policy.calculatePaceBonus({
        baseXp: 100,
        activeDurationMs: 60_000,
        baselineDurationMs: 120_000,
        hintsUsed: [3, 3, 3], // 30 XP penalty
        allCorrect: true,
      });

      assert.equal(resExceeding.bonusXp, 0);
    });

    test('estimates lesson baseline duration from exercise types', () => {
      const lesson: Lesson = {
        id: 'lesson-1',
        title: 'Intro',
        explanation: '',
        exercises: [multipleChoice, writeFunction],
      };

      // 30s + 120s = 150s = 150,000ms
      const baseline = policy.estimateLessonBaselineMs(lesson);
      assert.equal(baseline, 150_000);
    });
  });
});
