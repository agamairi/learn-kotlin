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

/** Streak freeze policy backed by a set of frozen dates. */
class SetStreakFreezePolicy(
    val frozenDates: Set<LocalDate>
) : StreakFreezePolicy {
    override fun isFrozen(date: LocalDate): Boolean = date in frozenDates
}

/**
 * Result of evaluating streaks and streak freezes over a learner's history.
 */
data class StreakFreezeState(
    val heldFreezes: Int,
    val frozenDates: Set<LocalDate>,
    val currentStreak: Int,
    val longestStreak: Int
)

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
 * When no explicit [freezePolicy] is provided, the Phase 3 streak freeze policy
 * is applied automatically: 1 freeze earned per 7 consecutive goal days (capped at 2),
 * and missed past days covered automatically by a held freeze.
 */
class DefaultStreakCalculator(
    private val freezePolicy: StreakFreezePolicy? = null
) : StreakCalculator {

    override fun calculateStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int {
        return if (freezePolicy != null) {
            calculateSummary(activityTimestamps, now, timeZone).currentStreak
        } else {
            calculateStreakState(activityTimestamps, now, timeZone).currentStreak
        }
    }

    /**
     * Evaluates the learner's full streak freeze state including held freezes,
     * dates covered by a freeze, and current / longest streak.
     */
    fun calculateStreakState(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): StreakFreezeState {
        val today = effectiveLocalDate(now, timeZone)
        val activeDays = activityTimestamps
            .asSequence()
            .filter { it <= now }
            .map { effectiveLocalDate(it, timeZone) }
            .filter { it <= today }
            .toSet()

        if (activeDays.isEmpty()) {
            return StreakFreezeState(
                heldFreezes = 0,
                frozenDates = emptySet(),
                currentStreak = 0,
                longestStreak = 0
            )
        }

        val earliest = activeDays.minOrNull() ?: today
        var day = earliest
        var heldFreezes = 0
        var consecutiveGoalDays = 0
        val frozenDays = mutableSetOf<LocalDate>()

        while (day <= today) {
            if (day in activeDays) {
                consecutiveGoalDays++
                if (consecutiveGoalDays % 7 == 0) {
                    heldFreezes = minOf(2, heldFreezes + 1)
                }
            } else if (day < today) {
                if (heldFreezes > 0) {
                    heldFreezes--
                    frozenDays.add(day)
                    consecutiveGoalDays = 0
                } else {
                    consecutiveGoalDays = 0
                }
            }
            day = day.nextDay()
        }

        val summary = calculateSummaryInternal(activeDays, frozenDays, today)

        return StreakFreezeState(
            heldFreezes = heldFreezes,
            frozenDates = frozenDays,
            currentStreak = summary.currentStreak,
            longestStreak = summary.longestStreak
        )
    }

    /**
     * Calculates both the current and longest streak for the supplied history.
     */
    fun calculateSummary(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): StreakSummary {
        if (freezePolicy == null) {
            val state = calculateStreakState(activityTimestamps, now, timeZone)
            return StreakSummary(currentStreak = state.currentStreak, longestStreak = state.longestStreak)
        }

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

        return calculateSummaryInternal(
            activeDays = activeDays,
            frozenDays = emptySet(),
            today = today,
            customPolicy = freezePolicy
        )
    }

    private fun calculateSummaryInternal(
        activeDays: Set<LocalDate>,
        frozenDays: Set<LocalDate>,
        today: LocalDate,
        customPolicy: StreakFreezePolicy? = null
    ): StreakSummary {
        val currentStart = if (isCounted(today, activeDays, frozenDays, customPolicy)) today else today.previousDay()
        val current = countBackwards(currentStart, activeDays, frozenDays, customPolicy)
        val earliest = activeDays.minOrNull() ?: today
        var day = earliest
        var run = 0
        var longest = 0
        while (day <= today) {
            if (isCounted(day, activeDays, frozenDays, customPolicy)) {
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
    ): Int = calculateStreak(activityTimestamps, now, timeZone)

    /** Calculates only the longest streak in the recorded history. */
    fun longestStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int = calculateSummary(activityTimestamps, now, timeZone).longestStreak

    private fun countBackwards(
        start: LocalDate,
        activeDays: Set<LocalDate>,
        frozenDays: Set<LocalDate>,
        customPolicy: StreakFreezePolicy?
    ): Int {
        var day = start
        var count = 0
        while (isCounted(day, activeDays, frozenDays, customPolicy)) {
            count++
            day = day.previousDay()
        }
        return count
    }

    private fun isCounted(
        day: LocalDate,
        activeDays: Set<LocalDate>,
        frozenDays: Set<LocalDate>,
        customPolicy: StreakFreezePolicy?
    ): Boolean =
        day in activeDays || day in frozenDays || customPolicy?.isFrozen(day) == true
}

/** Compatibility name for callers that use the conventional implementation suffix. */
typealias StreakCalculatorImpl = DefaultStreakCalculator

internal fun effectiveLocalDate(instant: Instant, timeZone: TimeZone): LocalDate {
    val localDateTime = instant.toLocalDateTime(timeZone)
    return if (localDateTime.hour < 4) localDateTime.date.previousDay() else localDateTime.date
}

private fun LocalDate.previousDay(): LocalDate = minus(DatePeriod(days = 1))

private fun LocalDate.nextDay(): LocalDate = minus(DatePeriod(days = -1))
