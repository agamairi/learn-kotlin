package dev.agam.learn.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private fun day(n: Int): LocalDate = LocalDate(2026, 1, n)

private fun days(range: IntRange): Set<LocalDate> = range.mapTo(mutableSetOf()) { day(it) }

class StreakFreezeTest {
    private val calculator = DefaultStreakCalculator()

    @Test
    fun sevenConsecutiveGoalDaysEarnOneFreeze() {
        val state = calculator.evaluate(days(1..7), today = day(8))

        assertEquals(1, state.heldFreezes)
        assertEquals(emptySet(), state.frozenDates)
        assertEquals(7, state.currentStreak)
    }

    @Test
    fun sixDaysEarnNothing() {
        assertEquals(0, calculator.evaluate(days(1..6), today = day(7)).heldFreezes)
    }

    @Test
    fun freezesStopAtTwoHeld() {
        assertEquals(1, calculator.evaluate(days(1..7), today = day(8)).heldFreezes)
        assertEquals(2, calculator.evaluate(days(1..14), today = day(15)).heldFreezes)
        assertEquals(2, calculator.evaluate(days(1..21), today = day(22)).heldFreezes)
    }

    @Test
    fun aMissedDayIsCoveredByAHeldFreezeAndTheStreakContinues() {
        // Seven goal days earn one freeze; day 8 is missed and covered; day 9 is a goal day.
        val goals = days(1..7) + day(9)
        val state = calculator.evaluate(goals, today = day(9))

        assertEquals(setOf(day(8)), state.frozenDates)
        assertEquals(0, state.heldFreezes)
        assertEquals(9, state.currentStreak)
        assertEquals(9, state.longestStreak)
    }

    @Test
    fun twoMissedDaysWithOneFreezeBreakTheStreak() {
        // Day 8 is covered by the only freeze; day 9 is missed with nothing left to cover it.
        val goals = days(1..7) + day(10)
        val state = calculator.evaluate(goals, today = day(10))

        assertEquals(setOf(day(8)), state.frozenDates)
        assertEquals(0, state.heldFreezes)
        assertEquals(1, state.currentStreak)
        assertEquals(8, state.longestStreak)
    }

    @Test
    fun twoHeldFreezesCoverTwoMissedDays() {
        // Twenty-one goal days hold two freezes. Days 22 and 23 are each covered; day 24 is a goal day.
        val goals = days(1..21) + day(24)
        val state = calculator.evaluate(goals, today = day(24))

        assertEquals(setOf(day(22), day(23)), state.frozenDates)
        assertEquals(0, state.heldFreezes)
        assertEquals(24, state.currentStreak)
    }

    @Test
    fun aSpentFreezeRestartsTheCountOfSevenGoalDays() {
        // Day 8 is covered and the count restarts, so seven more goal days (9 to 15) earn the next freeze.
        val goals = days(1..7) + days(9..15)
        val state = calculator.evaluate(goals, today = day(15))

        assertEquals(setOf(day(8)), state.frozenDates)
        assertEquals(1, state.heldFreezes)
    }

    @Test
    fun todayIsNotMissedYetSoNoFreezeIsSpentForIt() {
        val state = calculator.evaluate(days(1..7), today = day(8))

        assertEquals(1, state.heldFreezes)
        assertEquals(emptySet(), state.frozenDates)
        assertEquals(7, state.currentStreak)
    }

    @Test
    fun evaluatingTheSameHistoryTwiceGivesTheSameResult() {
        val goals = days(1..7) + day(9)

        assertEquals(calculator.evaluate(goals, day(9)), calculator.evaluate(goals, day(9)))
    }

    @Test
    fun goalDaysAfterTodayAreIgnored() {
        val state = calculator.evaluate(days(1..7) + day(20), today = day(7))

        assertEquals(1, state.heldFreezes)
        assertEquals(7, state.currentStreak)
    }
}
