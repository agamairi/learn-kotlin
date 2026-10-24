package dev.agam.learn.fake

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeStreakCalculatorTest {

    private lateinit var calculator: FakeStreakCalculator
    private val timeZone = TimeZone.of("America/New_York")

    @Before
    fun setUp() {
        calculator = FakeStreakCalculator()
    }

    @Test
    fun testEmptyTimestampsReturnsZero() {
        val now = Instant.parse("2026-10-24T12:00:00Z")
        val streak = calculator.calculateStreak(emptyList(), now, timeZone)
        assertEquals(0, streak)
    }

    @Test
    fun testActiveTodayReturnsOne() {
        val now = Instant.parse("2026-10-24T14:00:00Z")
        val activity = Instant.parse("2026-10-24T13:00:00Z")
        val streak = calculator.calculateStreak(listOf(activity), now, timeZone)
        assertEquals(1, streak)
    }

    @Test
    fun testConsecutiveDaysAcrossCutoff() {
        // America/New_York is UTC-4 in October (EDT)
        // 2026-10-24 14:00:00 UTC = 10:00 AM EDT (day 2026-10-24)
        val now = Instant.parse("2026-10-24T14:00:00Z")
        // Activity 1: 2026-10-24 14:00:00Z (10:00 AM EDT -> day 2026-10-24)
        val todayActivity = Instant.parse("2026-10-24T14:00:00Z")
        // Activity 2: 2026-10-23 20:00:00Z (4:00 PM EDT -> day 2026-10-23)
        val yesterdayActivity = Instant.parse("2026-10-23T20:00:00Z")
        // Activity 3: 2026-10-22 18:00:00Z (2:00 PM EDT -> day 2026-10-22)
        val dayBeforeYesterday = Instant.parse("2026-10-22T18:00:00Z")

        val streak = calculator.calculateStreak(
            listOf(todayActivity, yesterdayActivity, dayBeforeYesterday),
            now,
            timeZone
        )
        assertEquals(3, streak)
    }

    @Test
    fun testActivityBefore4AmCountsTowardPreviousDay() {
        // 2026-10-24 06:00:00 UTC = 2:00 AM EDT on 2026-10-24.
        // With 4 AM cutoff, 2:00 AM on Oct 24 counts as learning day 2026-10-23!
        val nightOwlActivity = Instant.parse("2026-10-24T06:00:00Z")

        // Now is 2026-10-24 14:00:00 UTC = 10:00 AM EDT on Oct 24.
        val now = Instant.parse("2026-10-24T14:00:00Z")

        // Since Oct 24 has no practice after 4 AM, but Oct 23 was active (via the 2 AM session), streak is 1!
        val streak = calculator.calculateStreak(listOf(nightOwlActivity), now, timeZone)
        assertEquals(1, streak)
    }

    @Test
    fun testGapInDaysResetsStreak() {
        val now = Instant.parse("2026-10-24T14:00:00Z")
        // Activity was 3 days ago (2026-10-21)
        val oldActivity = Instant.parse("2026-10-21T14:00:00Z")

        val streak = calculator.calculateStreak(listOf(oldActivity), now, timeZone)
        assertEquals(0, streak)
    }
}
