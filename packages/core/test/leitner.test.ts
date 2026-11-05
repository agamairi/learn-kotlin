import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { LeitnerScheduler } from '../src/domain/leitner.ts';
import type { ReviewCard } from '../src/models/progress.ts';

describe('LeitnerScheduler', () => {
  const scheduler = new LeitnerScheduler();

  test('correct answers advance box and schedule using Leitner intervals', () => {
    const card: ReviewCard = { itemId: 'item-1', box: 1, dueEpochDay: 10 };

    // Box 1 -> 2 (interval 2 days)
    const next1 = scheduler.scheduleNextReview(card, true, 10);
    assert.deepEqual(next1, { itemId: 'item-1', box: 2, dueEpochDay: 12 });

    // Box 2 -> 3 (interval 4 days)
    const next2 = scheduler.scheduleNextReview(next1, true, 12);
    assert.deepEqual(next2, { itemId: 'item-1', box: 3, dueEpochDay: 16 });

    // Box 3 -> 4 (interval 8 days)
    const next3 = scheduler.scheduleNextReview(next2, true, 16);
    assert.deepEqual(next3, { itemId: 'item-1', box: 4, dueEpochDay: 24 });

    // Box 4 -> 5 (interval 16 days)
    const next4 = scheduler.scheduleNextReview(next3, true, 24);
    assert.deepEqual(next4, { itemId: 'item-1', box: 5, dueEpochDay: 40 });

    // Box 5 stays in Box 5 (interval 16 days)
    const next5 = scheduler.scheduleNextReview(next4, true, 40);
    assert.deepEqual(next5, { itemId: 'item-1', box: 5, dueEpochDay: 56 });
  });

  test('wrong answers return card to box 1 due tomorrow', () => {
    const card: ReviewCard = { itemId: 'item-1', box: 4, dueEpochDay: 10 };
    const failed = scheduler.scheduleNextReview(card, false, 20);
    assert.deepEqual(failed, { itemId: 'item-1', box: 1, dueEpochDay: 21 });
  });

  test('new card is created in box 1 due tomorrow', () => {
    const newCard = scheduler.newCard('new-item', 100);
    assert.deepEqual(newCard, { itemId: 'new-item', box: 1, dueEpochDay: 101 });
  });

  test('dueItems filters cards due on or before epoch day', () => {
    const cards: ReviewCard[] = [
      { itemId: 'early', box: 1, dueEpochDay: 4 },
      { itemId: 'today', box: 2, dueEpochDay: 5 },
      { itemId: 'later', box: 3, dueEpochDay: 6 },
    ];

    const due = scheduler.dueItems(cards, 5);
    assert.deepEqual(
      due.map((c) => c.itemId),
      ['early', 'today']
    );
  });
});
