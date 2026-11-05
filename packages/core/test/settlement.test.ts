import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  ProgressRecorder,
  type Clock,
  type ExerciseAttempt,
  type ProblemPartAttempt,
} from '../src/domain/settlement.ts';
import {
  InMemoryProgressRepository,
  InMemorySettingsRepository,
  StaticCourseRepository,
} from '../src/domain/repository.ts';
import { LeitnerScheduler } from '../src/domain/leitner.ts';
import { DefaultXpPolicy } from '../src/domain/xp-policy.ts';
import { isLessonComplete } from '../src/domain/lesson-completion.ts';
import { toEpochDay } from '../src/domain/date-time.ts';
import type {
  MultipleChoiceExercise,
  ClozeExercise,
  WriteFunctionExercise,
  MultiPartProblemExercise,
} from '../src/models/exercise.ts';
import type { Course } from '../src/models/course.ts';
import type { ReviewCard } from '../src/models/progress.ts';

class MutableClock implements Clock {
  currentMillis: number;

  constructor(currentMillis: number) {
    this.currentMillis = currentMillis;
  }

  now(): number {
    return this.currentMillis;
  }
}

describe('Idempotent Lesson Settlement and ProgressRecorder', () => {
  const toronto = 'America/Toronto';

  const cardMc: MultipleChoiceExercise = {
    type: 'multiple_choice',
    id: 'card-mc',
    prompt: 'Pick',
    options: ['A', 'B'],
    correctIndex: 0,
    explanation: '',
  };

  const cardCloze: ClozeExercise = {
    type: 'cloze',
    id: 'card-cloze',
    prompt: 'Fill',
    template: '____1',
    blanks: [{ placeholder: '____1', accepted: ['val'] }],
    explanation: '',
  };

  const drill: WriteFunctionExercise = {
    type: 'write_function',
    id: 'drill',
    prompt: 'Write',
    starterCode: 'fun f() = TODO()',
    hiddenTests: '',
    referenceSolution: 'fun f() = 1',
  };

  const problem: MultiPartProblemExercise = {
    type: 'multi_part_problem',
    id: 'problem',
    prompt: 'Problem',
    story: 'Story',
    parts: [
      { id: 'part-1', prompt: 'one', hiddenTests: '', referenceSolution: '', expected: '' },
      { id: 'part-2', prompt: 'two', hiddenTests: '', referenceSolution: '', expected: '' },
    ],
  };

  const testCourse: Course = {
    id: 'course-1',
    language: 'kotlin',
    title: 'Test Course',
    description: '',
    chapters: [
      {
        id: 'ch-1',
        title: 'Chapter 1',
        summary: '',
        lessons: [
          {
            id: 'lesson-1',
            title: 'Lesson 1',
            explanation: '',
            exercises: [cardMc, cardCloze, drill, problem],
          },
        ],
      },
    ],
  };

  let progressRepo: InMemoryProgressRepository;
  let settingsRepo: InMemorySettingsRepository;
  let clock: MutableClock;
  let recorder: ProgressRecorder;

  beforeEach(() => {
    progressRepo = new InMemoryProgressRepository();
    settingsRepo = new InMemorySettingsRepository();
    // 2026-01-06T12:00:00Z -> 07:00 in Toronto on Jan 6
    clock = new MutableClock(new Date('2026-01-06T12:00:00Z').getTime());
    recorder = new ProgressRecorder(
      progressRepo,
      new StaticCourseRepository([testCourse]),
      settingsRepo,
      new LeitnerScheduler(),
      new DefaultXpPolicy(),
      clock,
      toronto
    );
  });

  describe('Standalone Progress Recording and Idempotency', () => {
    test('correct standalone card stores attempt and adds daily XP', async () => {
      const outcome = await recorder.record({
        attemptId: 'a1',
        exerciseId: 'card-mc',
        correct: true,
      });

      assert.equal(outcome.type, 'recorded');
      if (outcome.type === 'recorded') {
        assert.equal(outcome.xpAwarded, 10);
      }

      const results = progressRepo.getExerciseResults();
      assert.equal(results.length, 1);
      assert.equal(results[0].exerciseId, 'card-mc');
      assert.equal(results[0].lessonId, 'lesson-1');
      assert.equal(results[0].chapterId, 'ch-1');

      const jan6Epoch = toEpochDay('2026-01-06');
      const act = progressRepo.getDailyActivity(jan6Epoch);
      assert.notEqual(act, null);
      assert.equal(act?.xpEarned, 10);
      assert.equal(act?.exercisesCompleted, 1);
    });

    test('repeated attemptId is idempotent: writes nothing and returns duplicate', async () => {
      const first = await recorder.record({
        attemptId: 'same-id',
        exerciseId: 'card-mc',
        correct: true,
      });
      assert.equal(first.type, 'recorded');

      const second = await recorder.record({
        attemptId: 'same-id',
        exerciseId: 'card-mc',
        correct: true,
      });
      assert.equal(second.type, 'duplicate');

      assert.equal(progressRepo.getExerciseResults().length, 1);
      const jan6Epoch = toEpochDay('2026-01-06');
      assert.equal(progressRepo.getDailyActivity(jan6Epoch)?.xpEarned, 10);
    });

    test('code drill pays 25 on first try, 15 on retry, and hint deductions apply', async () => {
      // First pass with hint level 1 (costs 2 XP) -> 23 XP
      const res = await recorder.record({
        attemptId: 'c1',
        exerciseId: 'drill',
        correct: true,
        aiHintsUsed: [1],
      });
      assert.equal(res.type, 'recorded');
      if (res.type === 'recorded') {
        assert.equal(res.xpAwarded, 23);
      }

      // Repeated pass on same drill pays 0 additional XP
      const res2 = await recorder.record({
        attemptId: 'c2',
        exerciseId: 'drill',
        correct: true,
      });
      assert.equal(res2.type, 'recorded');
      if (res2.type === 'recorded') {
        assert.equal(res2.xpAwarded, 0);
      }
    });

    test('multipart problem parts pay 50 once per part', async () => {
      const p1 = await recorder.record({
        attemptId: 'p1',
        problemId: 'problem',
        partId: 'part-1',
        correct: true,
      });
      assert.equal(p1.type, 'recorded');
      if (p1.type === 'recorded') assert.equal(p1.xpAwarded, 50);

      // Part 1 again pays 0
      const p1Again = await recorder.record({
        attemptId: 'p2',
        problemId: 'problem',
        partId: 'part-1',
        correct: true,
      });
      assert.equal(p1Again.type, 'recorded');
      if (p1Again.type === 'recorded') assert.equal(p1Again.xpAwarded, 0);

      // Part 2 pays 50
      const p2 = await recorder.record({
        attemptId: 'p3',
        problemId: 'problem',
        partId: 'part-2',
        correct: true,
      });
      assert.equal(p2.type, 'recorded');
      if (p2.type === 'recorded') assert.equal(p2.xpAwarded, 50);

      // Problem completion pays 0 and completes exercise
      const done = await recorder.record({
        attemptId: 'done',
        exerciseId: 'problem',
        correct: true,
      });
      assert.equal(done.type, 'recorded');
      if (done.type === 'recorded') assert.equal(done.xpAwarded, 0);

      const jan6Epoch = toEpochDay('2026-01-06');
      const act = progressRepo.getDailyActivity(jan6Epoch);
      assert.equal(act?.xpEarned, 100);
      assert.equal(act?.exercisesCompleted, 1);
    });
  });

  describe('Staged Lesson Runs and Idempotent Settlement', () => {
    test('lesson run stages attempts without durable writes until settlement', async () => {
      settingsRepo.setDailyGoalXp(100);
      const began = await recorder.beginLessonRun('lesson-1', 'run-abc');
      assert.equal(began, true);

      // Mistake on card-mc -> stages mistake
      await recorder.record({
        attemptId: 'run-wrong',
        exerciseId: 'card-mc',
        correct: false,
        lessonRunId: 'run-abc',
      });
      // Mistake retry pass
      await recorder.record({
        attemptId: 'run-retry',
        exerciseId: 'card-mc',
        correct: true,
        isLessonRetry: true,
        lessonRunId: 'run-abc',
      });
      // Pass cloze
      await recorder.record({
        attemptId: 'run-cloze',
        exerciseId: 'card-cloze',
        correct: true,
        lessonRunId: 'run-abc',
      });
      // Pass drill with hint level 1 (25 - 2 = 23 XP)
      await recorder.record({
        attemptId: 'run-drill',
        exerciseId: 'drill',
        correct: true,
        aiHintsUsed: [1],
        lessonRunId: 'run-abc',
      });
      // Pass parts and complete problem (50 + 50 = 100 XP)
      await recorder.record({
        attemptId: 'run-p1',
        problemId: 'problem',
        partId: 'part-1',
        correct: true,
        lessonRunId: 'run-abc',
      });
      await recorder.record({
        attemptId: 'run-p2',
        problemId: 'problem',
        partId: 'part-2',
        correct: true,
        lessonRunId: 'run-abc',
      });
      await recorder.record({
        attemptId: 'run-p-done',
        exerciseId: 'problem',
        correct: true,
        lessonRunId: 'run-abc',
      });

      // Total XP staged: 10 (card-mc retry) + 10 (cloze) + 23 (drill) + 50 (part 1) + 50 (part 2) = 143 XP
      assert.equal(recorder.lessonRunXp('run-abc'), 143);

      // Repository must still be completely empty!
      assert.equal(progressRepo.getExerciseResults().length, 0);
      assert.equal(progressRepo.getDailyActivities().length, 0);
      assert.equal(progressRepo.isLessonCompleted('lesson-1'), false);

      // Settle the lesson run with pace bonus (active 60s vs baseline 120s)
      const settlement = await recorder.settleLessonRun('lesson-1', 'run-abc', {
        activeDurationMs: 60_000,
        baselineDurationMs: 120_000,
      });

      assert.equal(settlement.type, 'settled');
      if (settlement.type === 'settled') {
        assert.equal(settlement.baseXp, 143);
        // Capped pace bonus (25% of 143 = ~36 XP)
        assert.equal(settlement.paceBonusXp, 36);
        assert.equal(settlement.totalXp, 179);
      }

      // Check durable commit in repository
      assert.equal(progressRepo.isLessonCompleted('lesson-1'), true);
      assert.equal(progressRepo.getExerciseResults().length, 7);

      const jan6Epoch = toEpochDay('2026-01-06');
      const act = progressRepo.getDailyActivity(jan6Epoch);
      assert.notEqual(act, null);
      assert.equal(act?.xpEarned, 179);
      assert.equal(act?.exercisesCompleted, 4);
      assert.equal(act?.goalMet, true);

      // Seeding review card for missed card-mc is created due tomorrow
      const seededCard = progressRepo.getReviewCard('card-mc');
      assert.notEqual(seededCard, null);
      assert.equal(seededCard?.box, 1);
      assert.equal(seededCard?.dueEpochDay, jan6Epoch + 1);

      // Second settlement attempt with the SAME runId is IDEMPOTENT: returns duplicate
      const duplicateSettlement = await recorder.settleLessonRun('lesson-1', 'run-abc');
      assert.equal(duplicateSettlement.type, 'duplicate');

      // Database totals unchanged
      assert.equal(progressRepo.getDailyActivity(jan6Epoch)?.xpEarned, 179);
    });

    test('abandonLessonRun discards staged progress and allows fresh run', async () => {
      await recorder.beginLessonRun('lesson-1', 'abandon-run');
      await recorder.record({
        attemptId: 'ab-1',
        exerciseId: 'card-mc',
        correct: true,
        lessonRunId: 'abandon-run',
      });
      assert.equal(recorder.lessonRunXp('abandon-run'), 10);
      recorder.abandonLessonRun('abandon-run');

      assert.equal(progressRepo.getExerciseResults().length, 0);

      // New run can be started
      await recorder.beginLessonRun('lesson-1', 'fresh-run');
      await recorder.record({
        attemptId: 'fr-1',
        exerciseId: 'card-mc',
        correct: true,
        lessonRunId: 'fresh-run',
      });
      assert.equal(recorder.lessonRunXp('fresh-run'), 10);
    });
  });

  describe('isLessonComplete logic', () => {
    test('evaluates completed when lesson is in saved completions', () => {
      const lesson = testCourse.chapters[0].lessons[0];
      assert.equal(isLessonComplete(lesson, new Set(['lesson-1']), new Set()), true);
      assert.equal(isLessonComplete(lesson, new Set(), new Set()), false);
    });

    test('evaluates completed when all exercises have passed', () => {
      const lesson = testCourse.chapters[0].lessons[0];
      const allPassed = new Set(['card-mc', 'card-cloze', 'drill', 'problem']);
      assert.equal(isLessonComplete(lesson, new Set(), allPassed), true);

      const partial = new Set(['card-mc', 'card-cloze']);
      assert.equal(isLessonComplete(lesson, new Set(), partial), false);
    });
  });
});
