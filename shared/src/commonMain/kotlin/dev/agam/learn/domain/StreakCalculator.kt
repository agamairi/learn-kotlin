package dev.agam.learn.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The two streak values calculated from a learner's qualifying activity.
 *
 * [currentStreak] ends on the effective local day for [now], or on the
 * preceding effective day when the current day has no activity.
 */
data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int
)

/**
 * Extension point for a future streak-freeze policy.
 *
 * The default implementation never freezes a day; Phase 3 can provide a
 * policy backed by the learner's available freezes without changing streak
 * calculation callers.
 */
fun interface StreakFreezePolicy {
    fun isFrozen(date: LocalDate): Boolean
}

/** The v1 policy: no inactive days are covered by a freeze. */
object NoStreakFreezes : StreakFreezePolicy {
    override fun isFrozen(date: LocalDate): Boolean = false
}

/**
 * Calculates learner streaks with a 4:00 AM local day rollover boundary.
 */
interface StreakCalculator {
    /**
     * Calculates the streak count from a set of activity timestamps.
     *
     * @param activityTimestamps Instants at which qualifying learning activities took place.
     * @param now Current instant.
     * @param timeZone Time zone of the user device.
     * @return Number of consecutive active days.
     */
    fun calculateStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int
}

/**
 * Calculates streaks using a 04:00 local rollover and no Android dependencies.
 *
 * Every timestamp supplied to this class represents a day whose daily goal was
 * met. A future timestamp is ignored. Streak freezes are deliberately not
 * applied here; the summary calculation leaves that policy at the call site
 * for a later phase.
 */
class DefaultStreakCalculator(
    private val freezePolicy: StreakFreezePolicy = NoStreakFreezes
) : StreakCalculator {

    override fun calculateStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int = calculateSummary(activityTimestamps, now, timeZone).currentStreak

    /**
     * Calculates both the current and longest streak for the supplied history.
     */
    fun calculateSummary(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): StreakSummary {
        val today = effectiveLocalDate(now, timeZone)
        val activeDays = activityTimestamps
            .asSequence()
            .filter { it <= now }
            .map { effectiveLocalDate(it, timeZone) }
            .filter { it <= today }
            .toSet()

        if (activeDays.isEmpty()) {
            return StreakSummary(currentStreak = 0, longestStreak = 0)
        }

        val currentStart = if (isCounted(today, activeDays)) today else today.previousDay()
        val current = countBackwards(currentStart, activeDays)
        val earliest = activeDays.minOrNull() ?: today
        var day = earliest
        var run = 0
        var longest = 0
        while (day <= today) {
            if (isCounted(day, activeDays)) {
                run++
                longest = maxOf(longest, run)
            } else {
                run = 0
            }
            day = day.nextDay()
        }

        return StreakSummary(currentStreak = current, longestStreak = longest)
    }

    /** Alias for callers that prefer an explicit plural result name. */
    fun calculateStreaks(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): StreakSummary = calculateSummary(activityTimestamps, now, timeZone)

    /** Calculates only the current streak. */
    fun currentStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int = calculateSummary(activityTimestamps, now, timeZone).currentStreak

    /** Calculates only the longest streak in the recorded history. */
    fun longestStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int = calculateSummary(activityTimestamps, now, timeZone).longestStreak

    private fun countBackwards(start: LocalDate, activeDays: Set<LocalDate>): Int {
        var day = start
        var count = 0
        while (isCounted(day, activeDays)) {
            count++
            day = day.previousDay()
        }
        return count
    }

    private fun isCounted(day: LocalDate, activeDays: Set<LocalDate>): Boolean =
        day in activeDays || freezePolicy.isFrozen(day)
}

/** Compatibility name for callers that use the conventional implementation suffix. */
typealias StreakCalculatorImpl = DefaultStreakCalculator

internal fun effectiveLocalDate(instant: Instant, timeZone: TimeZone): LocalDate {
    val localDateTime = instant.toLocalDateTime(timeZone)
    return if (localDateTime.hour < 4) localDateTime.date.previousDay() else localDateTime.date
}

private fun LocalDate.previousDay(): LocalDate = minus(DatePeriod(days = 1))

private fun LocalDate.nextDay(): LocalDate = minus(DatePeriod(days = -1))
