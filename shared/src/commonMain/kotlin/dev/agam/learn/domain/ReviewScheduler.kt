package dev.agam.learn.domain

import dev.agam.learn.model.ReviewCard

/**
 * Spaced repetition scheduler based on the 5-box Leitner algorithm.
 * Intervals: Box 1 (1 day), Box 2 (2 days), Box 3 (4 days), Box 4 (8 days), Box 5 (16 days).
 */
interface ReviewScheduler {
    /**
     * Determines the next review schedule for a card.
     *
     * @param card Existing review card with current box number.
     * @param correct Whether the user correctly answered the review card.
     * @param currentEpochDay Today's epoch day number.
     * @return Updated review card with new box and dueEpochDay.
     */
    fun scheduleNextReview(
        card: ReviewCard,
        correct: Boolean,
        currentEpochDay: Long
    ): ReviewCard
}
