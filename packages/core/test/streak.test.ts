import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { DefaultStreakCalculator } from '../src/domain/streak.ts';

const utc = 'UTC';
const toronto = 'America/Toronto';

function days(start: number, end: number): string[] {
  const result: string[] = [];
  for (let i = start; i <= end; i++) {
    result.push(`2026-01-${String(i).padStart(2, '0')}`);
  }
  return result;
}

describe('StreakCalculator', () => {
  const calculator = new DefaultStreakCalculator();

  describe('Timestamps and Rollover Evaluation', () => {
    test('empty history has no streak', () => {
      const summary = calculator.calculateSummary([], '2026-01-10T12:00:00Z', utc);
      assert.deepEqual(summary, { currentStreak: 0, longestStreak: 0 });
    });

    test('activity before 04:00 local time belongs to previous day', () => {
      const summary = calculator.calculateSummary(
        ['2026-01-06T03:59:59Z', '2026-01-06T04:00:00Z'],
        '2026-01-07T12:00:00Z',
        utc
      );
      assert.deepEqual(summary, { currentStreak: 2, longestStreak: 2 });
    });

    test('current streak may end yesterday and gaps limit longest streak', () => {
      const summary = calculator.calculateSummary(
        [
          '2026-01-01T12:00:00Z',
          '2026-01-02T12:00:00Z',
          '2026-01-04T12:00:00Z',
          '2026-01-05T12:00:00Z',
        ],
        '2026-01-06T12:00:00Z',
        utc
      );
      assert.equal(summary.currentStreak, 2);
      assert.equal(summary.longestStreak, 2);
    });

    test('midnight and cutoff use local date in Toronto', () => {
      const summary = calculator.calculateSummary(
        [
          '2026-01-06T04:59:59Z', // 23:59:59 on Jan 5 in Toronto
          '2026-01-06T05:00:00Z', // 00:00:00 on Jan 6 in Toronto
          '2026-01-06T08:59:59Z', // 03:59:59, still Jan 5's effective day
          '2026-01-06T09:00:00Z', // 04:00:00, Jan 6's effective day
        ],
        '2026-01-07T15:00:00Z',
        toronto
      );
      assert.deepEqual(summary, { currentStreak: 2, longestStreak: 2 });
    });

    test('spring DST change still uses 04:00 local clock', () => {
      const summary = calculator.calculateSummary(
        [
          '2026-03-08T06:59:59Z', // 01:59:59 EST -> effective Mar 7
          '2026-03-08T08:00:00Z', // 04:00:00 EDT -> effective Mar 8
        ],
        '2026-03-09T12:00:00Z',
        toronto
      );
      assert.deepEqual(summary, { currentStreak: 2, longestStreak: 2 });
    });
  });

  describe('Streak Freezes', () => {
    test('seven consecutive goal days earn one freeze', () => {
      const state = calculator.evaluate(days(1, 7), '2026-01-08');
      assert.equal(state.heldFreezes, 1);
      assert.deepEqual(state.frozenDates, []);
      assert.equal(state.currentStreak, 7);
    });

    test('six days earn nothing', () => {
      const state = calculator.evaluate(days(1, 6), '2026-01-07');
      assert.equal(state.heldFreezes, 0);
    });

    test('held freezes stop at two', () => {
      assert.equal(calculator.evaluate(days(1, 7), '2026-01-08').heldFreezes, 1);
      assert.equal(calculator.evaluate(days(1, 14), '2026-01-15').heldFreezes, 2);
      assert.equal(calculator.evaluate(days(1, 21), '2026-01-22').heldFreezes, 2);
    });

    test('a missed day is covered by a held freeze and the streak continues', () => {
      const goals = [...days(1, 7), '2026-01-09'];
      const state = calculator.evaluate(goals, '2026-01-09');

      assert.deepEqual(state.frozenDates, ['2026-01-08']);
      assert.equal(state.heldFreezes, 0);
      assert.equal(state.currentStreak, 9);
      assert.equal(state.longestStreak, 9);
    });

    test('two missed days with one freeze break the streak', () => {
      const goals = [...days(1, 7), '2026-01-10'];
      const state = calculator.evaluate(goals, '2026-01-10');

      assert.deepEqual(state.frozenDates, ['2026-01-08']);
      assert.equal(state.heldFreezes, 0);
      assert.equal(state.currentStreak, 1);
      assert.equal(state.longestStreak, 8);
    });

    test('two held freezes cover two missed days', () => {
      const goals = [...days(1, 21), '2026-01-24'];
      const state = calculator.evaluate(goals, '2026-01-24');

      assert.deepEqual(state.frozenDates, ['2026-01-22', '2026-01-23']);
      assert.equal(state.heldFreezes, 0);
      assert.equal(state.currentStreak, 24);
    });

    test('spent freeze restarts count of seven goal days', () => {
      const goals = [...days(1, 7), ...days(9, 15)];
      const state = calculator.evaluate(goals, '2026-01-15');

      assert.deepEqual(state.frozenDates, ['2026-01-08']);
      assert.equal(state.heldFreezes, 1);
    });

    test('today is not missed yet so no freeze is spent for it', () => {
      const state = calculator.evaluate(days(1, 7), '2026-01-08');
      assert.equal(state.heldFreezes, 1);
      assert.deepEqual(state.frozenDates, []);
      assert.equal(state.currentStreak, 7);
    });

    test('evaluating the same history twice is idempotent and deterministic', () => {
      const goals = [...days(1, 7), '2026-01-09'];
      const res1 = calculator.evaluate(goals, '2026-01-09');
      const res2 = calculator.evaluate(goals, '2026-01-09');
      assert.deepEqual(res1, res2);
    });

    test('goal days after today are ignored', () => {
      const state = calculator.evaluate([...days(1, 7), '2026-01-20'], '2026-01-07');
      assert.equal(state.heldFreezes, 1);
      assert.equal(state.currentStreak, 7);
    });
  });
});
