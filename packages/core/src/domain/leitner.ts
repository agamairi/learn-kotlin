import type { ReviewCard } from '../models/progress.ts';

export const MIN_BOX = 1;
export const MAX_BOX = 5;
export const LEITNER_INTERVALS = [1, 2, 4, 8, 16] as const;

/**
 * Spaced repetition scheduler based on the 5-box Leitner algorithm.
 * Intervals: Box 1 (1 day), Box 2 (2 days), Box 3 (4 days), Box 4 (8 days), Box 5 (16 days).
 */
export interface ReviewScheduler {
  /**
   * Determines the next review schedule for a card.
   *
   * @param card Existing review card with current box number.
   * @param correct Whether the user correctly answered the review card.
   * @param currentEpochDay Today's epoch day number.
   * @returns Updated review card with new box and dueEpochDay.
   */
  scheduleNextReview(
    card: ReviewCard,
    correct: boolean,
    currentEpochDay: number
  ): ReviewCard;

  /** Creates the card for an item that was just missed: box 1, due tomorrow. */
  newCard(itemId: string, currentEpochDay: number): ReviewCard;

  /** Returns cards due on or before epochDay. */
  dueItems(cards: ReviewCard[], epochDay: number): ReviewCard[];
}

export class LeitnerScheduler implements ReviewScheduler {
  scheduleNextReview(
    card: ReviewCard,
    correct: boolean,
    currentEpochDay: number
  ): ReviewCard {
    const currentBox = Math.max(MIN_BOX, Math.min(MAX_BOX, card.box));
    const nextBox = correct ? Math.min(MAX_BOX, currentBox + 1) : MIN_BOX;
    const intervalDays = LEITNER_INTERVALS[nextBox - MIN_BOX];

    return {
      itemId: card.itemId,
      box: nextBox,
      dueEpochDay: currentEpochDay + intervalDays,
    };
  }

  newCard(itemId: string, currentEpochDay: number): ReviewCard {
    return {
      itemId,
      box: MIN_BOX,
      dueEpochDay: currentEpochDay + 1,
    };
  }

  dueItems(cards: ReviewCard[], epochDay: number): ReviewCard[] {
    return cards.filter((card) => card.dueEpochDay <= epochDay);
  }

  dueCards(cards: ReviewCard[], epochDay: number): ReviewCard[] {
    return this.dueItems(cards, epochDay);
  }

  listDueItems(cards: ReviewCard[], epochDay: number): ReviewCard[] {
    return this.dueItems(cards, epochDay);
  }
}
