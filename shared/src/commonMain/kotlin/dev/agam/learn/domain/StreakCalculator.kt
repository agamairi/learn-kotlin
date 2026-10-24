package dev.agam.learn.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Hour at which a new learning day starts in the device time zone. */
private const val DAY_ROLLOVER_HOUR: Int = 4

/** One streak freeze is earned for every this many consecutive goal days. */
const val FREEZE_EVERY_GOAL_DAYS: Int = 7

/** A learner never holds more than this many freezes. */
const val MAX_HELD_FREEZES: Int = 2

/**
 * The learner's effective day for [instant]: activity before 04:00 local time belongs to the
 * previous day. This is the only place the rollover is defined; every caller uses it.
 */
fun effectiveLocalDate(instant: Instant, timeZone: TimeZone): LocalDate {
    val local = instant.toLocalDateTime(timeZone)
    return if (local.hour < DAY_ROLLOVER_HOUR) local.date.minus(DatePeriod(days = 1)) else local.date
}

/**
 * The two streak values calculated from a learner's goal days.
 *
 * [currentStreak] ends on the effective local day for [now], or on the
 * preceding effective day when the current day has no activity.
 */
data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int
)

/**
 * The streak state for the learner's history, evaluated for one effective day.
 *
 * [frozenDates] are missed past days that a held freeze covered; they keep the streak going.
 * [heldFreezes] is how many freezes are available after every past day was evaluated.
 */
data class StreakFreezeState(
    val heldFreezes: Int,
    val frozenDates: Set<LocalDate>,
    val currentStreak: Int,
    val longestStreak: Int
)

/**
 * Calculates learner streaks with the 04:00 local rollover.
 */
interface StreakCalculator {
    /**
     * Calculates the streak count from a set of activity timestamps.
     *
     * @param activityTimestamps Instants at which qualifying learning activities took place.
     * @param now Current instant.
     * @param timeZone Time zone of the user device.
     * @return Number of consecutive active days, including days covered by a freeze.
     */
    fun calculateStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int
}

/**
 * Streak calculation with streak freezes and no Android dependencies.
 *
 * A "goal day" is an effective day on which the learner met the daily XP goal. The learner earns one
 * freeze per [FREEZE_EVERY_GOAL_DAYS] consecutive goal days, holds at most [MAX_HELD_FREEZES], and a
 * missed past day is covered automatically by a held freeze. A missed day without a freeze ends the run.
 */
class DefaultStreakCalculator : StreakCalculator {

    override fun calculateStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int = calculateStreakState(activityTimestamps, now, timeZone).currentStreak

    /** Current and longest streak for the supplied activity timestamps. */
    fun calculateSummary(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): StreakSummary {
        val state = calculateStreakState(activityTimestamps, now, timeZone)
        return StreakSummary(currentStreak = state.currentStreak, longestStreak = state.longestStreak)
    }

    /**
     * Evaluates the full streak state for activity timestamps. Timestamps after [now] are ignored.
     */
    fun calculateStreakState(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): StreakFreezeState {
        val today = effectiveLocalDate(now, timeZone)
        val goalDays = activityTimestamps
            .asSequence()
            .filter { it <= now }
            .map { effectiveLocalDate(it, timeZone) }
            .filter { it <= today }
            .toSet()
        return evaluate(goalDays, today)
    }

    /**
     * Evaluates streaks and freezes from the days on which the goal was met, as of [today].
     *
     * Days after [today] are ignored. The result depends only on [goalDays] and [today], so
     * evaluating the same history again gives the same answer.
     */
    fun evaluate(goalDays: Set<LocalDate>, today: LocalDate): StreakFreezeState {
        val goals = goalDays.filter { it <= today }.sorted()
        if (goals.isEmpty()) {
            return StreakFreezeState(heldFreezes = 0, frozenDates = emptySet(), currentStreak = 0, longestStreak = 0)
        }
        val goalSet = goals.toSet()

        val frozen = mutableSetOf<LocalDate>()
        var held = 0
        var consecutiveGoalDays = 0
        var day = goals.first()
        while (day <= today) {
            if (day in goalSet) {
                consecutiveGoalDays++
                if (consecutiveGoalDays % FREEZE_EVERY_GOAL_DAYS == 0) {
                    held = minOf(MAX_HELD_FREEZES, held + 1)
                }
            } else {
                // A missed day breaks the run of consecutive goal days, covered or not.
                consecutiveGoalDays = 0
                // Today is not missed yet: the learner can still complete it.
                if (day < today && held > 0) {
                    held--
                    frozen.add(day)
                }
            }
            day = day.nextDay()
        }

        val counted = goalSet + frozen
        val currentStart = if (today in counted) today else today.previousDay()
        var current = 0
        var cursor = currentStart
        while (cursor in counted) {
            current++
            cursor = cursor.previousDay()
        }

        var longest = 0
        var run = 0
        day = goals.first()
        while (day <= today) {
            if (day in counted) {
                run++
                longest = maxOf(longest, run)
            } else {
                run = 0
            }
            day = day.nextDay()
        }

        return StreakFreezeState(
            heldFreezes = held,
            frozenDates = frozen,
            currentStreak = current,
            longestStreak = longest
        )
    }
}

/** Compatibility name for callers that use the conventional implementation suffix. */
typealias StreakCalculatorImpl = DefaultStreakCalculator

private fun LocalDate.previousDay(): LocalDate = minus(DatePeriod(days = 1))

private fun LocalDate.nextDay(): LocalDate = plus(DatePeriod(days = 1))
