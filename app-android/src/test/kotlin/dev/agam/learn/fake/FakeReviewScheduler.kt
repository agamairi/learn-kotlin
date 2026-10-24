package dev.agam.learn.fake

import dev.agam.learn.domain.ReviewScheduler
import dev.agam.learn.model.ReviewCard

/**
 * 5-box Leitner spaced repetition scheduler.
 * Intervals:
 * Box 1 -> 1 day
 * Box 2 -> 2 days
 * Box 3 -> 4 days
 * Box 4 -> 8 days
 * Box 5 -> 16 days
 */
class FakeReviewScheduler : ReviewScheduler {

    override fun scheduleNextReview(
        card: ReviewCard,
        correct: Boolean,
        currentEpochDay: Long
    ): ReviewCard {
        val newBox = if (correct) minOf(5, card.box + 1) else 1
        val intervalDays = when (newBox) {
            1 -> 1L
            2 -> 2L
            3 -> 4L
            4 -> 8L
            5 -> 16L
            else -> 1L
        }
        return card.copy(
            box = newBox,
            dueEpochDay = currentEpochDay + intervalDays
        )
    }
}
