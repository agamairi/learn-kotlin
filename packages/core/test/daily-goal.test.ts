import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { DefaultDailyGoalTracker } from '../src/domain/daily-goal.ts';
import type { ExerciseResult } from '../src/models/progress.ts';

const toronto = 'America/Toronto';
const utc = 'UTC';

describe('DailyGoalTracker', () => {
  const tracker = new DefaultDailyGoalTracker(50);

  test('default goal and ratio are properly clamped', () => {
    assert.equal(tracker.defaultTargetXp, 50);
    assert.equal(tracker.isGoalMet(50, 50), true);
    assert.equal(tracker.isGoalMet(49, 50), false);
    assert.equal(tracker.progressRatio(100, 50), 1.0);
    assert.equal(tracker.progressRatio(25, 50), 0.5);
    assert.equal(tracker.progressRatio(-10, 50), 0.0);
    assert.equal(tracker.progressRatio(0, 0), 1.0);
  });

  test('xp at 03:59:59 belongs to previous goal day across local rollover', () => {
    // 09:00:00Z on Jan 6 is 04:00:00 in Toronto (rollover hour)
    const now = '2026-01-06T09:00:00Z';
    const results: ExerciseResult[] = [
      {
        exerciseId: 'e1',
        correct: true,
        xpAwarded: 20,
        epochMillis: new Date('2026-01-06T08:59:59Z').getTime(), // 03:59:59 Toronto -> Jan 5
      },
      {
        exerciseId: 'e2',
        correct: true,
        xpAwarded: 30,
        epochMillis: new Date('2026-01-06T09:00:00Z').getTime(), // 04:00:00 Toronto -> Jan 6
      },
      {
        exerciseId: 'e3',
        correct: true,
        xpAwarded: 100,
        epochMillis: new Date('2026-01-06T10:00:00Z').getTime(), // Future relative to now
      },
    ];

    // Evaluated at 04:00:01 on Jan 6
    const xpToday = tracker.xpToday(results, '2026-01-06T09:00:01Z', toronto);
    assert.equal(xpToday, 30);

    const progress = tracker.progress(results, now, toronto);
    assert.equal(progress.xpToday, 30);
    assert.equal(progress.targetXp, 50);
    assert.equal(progress.isMet, false);
    assert.equal(progress.ratio, 0.6);
  });

  test('calculates today XP in UTC timezone correctly', () => {
    const results: ExerciseResult[] = [
      {
        exerciseId: 'u1',
        correct: true,
        xpAwarded: 10,
        epochMillis: new Date('2026-01-06T03:59:59Z').getTime(), // 03:59:59 UTC -> Jan 5
      },
      {
        exerciseId: 'u2',
        correct: true,
        xpAwarded: 15,
        epochMillis: new Date('2026-01-06T04:00:00Z').getTime(), // 04:00:00 UTC -> Jan 6
      },
    ];

    const xpJan6 = tracker.xpToday(results, '2026-01-06T12:00:00Z', utc);
    assert.equal(xpJan6, 15);
  });
});
