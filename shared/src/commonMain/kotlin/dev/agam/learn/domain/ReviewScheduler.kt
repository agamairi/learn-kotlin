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

/**
 * Five-box Leitner scheduler with intervals of 1, 2, 4, 8 and 16 days.
 */
class LeitnerScheduler : ReviewScheduler {

    override fun scheduleNextReview(
        card: ReviewCard,
        correct: Boolean,
        currentEpochDay: Long
    ): ReviewCard {
        val currentBox = card.box.coerceIn(MIN_BOX, MAX_BOX)
        val nextBox = if (correct) (currentBox + 1).coerceAtMost(MAX_BOX) else MIN_BOX
        return card.copy(
            box = nextBox,
            dueEpochDay = currentEpochDay + INTERVALS[nextBox - MIN_BOX]
        )
    }

    /** Creates a new card in box 1, due tomorrow. */
    fun newCard(itemId: String, currentEpochDay: Long): ReviewCard =
        ReviewCard(itemId = itemId, box = MIN_BOX, dueEpochDay = currentEpochDay + 1)

    /** Returns cards due on or before [epochDay], preserving input order. */
    fun dueItems(cards: Collection<ReviewCard>, epochDay: Long): List<ReviewCard> =
        cards.filter { it.dueEpochDay <= epochDay }

    /** Alias using the model's terminology. */
    fun dueCards(cards: Collection<ReviewCard>, epochDay: Long): List<ReviewCard> =
        dueItems(cards, epochDay)

    /** Alias for callers that use a verb-first name. */
    fun listDueItems(cards: Collection<ReviewCard>, epochDay: Long): List<ReviewCard> =
        dueItems(cards, epochDay)

    companion object {
        private const val MIN_BOX: Int = 1
        private const val MAX_BOX: Int = 5
        private val INTERVALS = intArrayOf(1, 2, 4, 8, 16)
    }
}
