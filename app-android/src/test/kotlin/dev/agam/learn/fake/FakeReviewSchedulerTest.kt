package dev.agam.learn.fake

import dev.agam.learn.model.ReviewCard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeReviewSchedulerTest {

    private lateinit var scheduler: FakeReviewScheduler

    @Before
    fun setUp() {
        scheduler = FakeReviewScheduler()
    }

    @Test
    fun testCorrectAnswerAdvancesBoxAndInterval() {
        val currentDay = 20000L
        val card1 = ReviewCard(itemId = "card-1", box = 1, dueEpochDay = currentDay)

        // Box 1 -> Box 2 (interval 2 days)
        val card2 = scheduler.scheduleNextReview(card1, correct = true, currentEpochDay = currentDay)
        assertEquals(2, card2.box)
        assertEquals(currentDay + 2L, card2.dueEpochDay)

        // Box 2 -> Box 3 (interval 4 days)
        val card3 = scheduler.scheduleNextReview(card2, correct = true, currentEpochDay = currentDay)
        assertEquals(3, card3.box)
        assertEquals(currentDay + 4L, card3.dueEpochDay)

        // Box 3 -> Box 4 (interval 8 days)
        val card4 = scheduler.scheduleNextReview(card3, correct = true, currentEpochDay = currentDay)
        assertEquals(4, card4.box)
        assertEquals(currentDay + 8L, card4.dueEpochDay)

        // Box 4 -> Box 5 (interval 16 days)
        val card5 = scheduler.scheduleNextReview(card4, correct = true, currentEpochDay = currentDay)
        assertEquals(5, card5.box)
        assertEquals(currentDay + 16L, card5.dueEpochDay)

        // Box 5 stays at 5
        val card5Max = scheduler.scheduleNextReview(card5, correct = true, currentEpochDay = currentDay)
        assertEquals(5, card5Max.box)
        assertEquals(currentDay + 16L, card5Max.dueEpochDay)
    }

    @Test
    fun testIncorrectAnswerResetsToBoxOne() {
        val currentDay = 20000L
        val card = ReviewCard(itemId = "card-1", box = 4, dueEpochDay = currentDay)

        val updated = scheduler.scheduleNextReview(card, correct = false, currentEpochDay = currentDay)
        assertEquals(1, updated.box)
        assertEquals(currentDay + 1L, updated.dueEpochDay)
    }
}
