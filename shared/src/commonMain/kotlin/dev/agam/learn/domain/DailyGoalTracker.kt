package dev.agam.learn.domain

import dev.agam.learn.model.ExerciseResult
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Tracks daily XP progress towards learner-defined daily targets.
 */
interface DailyGoalTracker {
    /**
     * Checks if the daily XP goal is met.
     */
    fun isGoalMet(xpToday: Int, targetXp: Int): Boolean

    /**
     * Returns progress ratio between 0.0 and 1.0 (clamped).
     */
    fun progressRatio(xpToday: Int, targetXp: Int): Float
}

/** Snapshot of XP progress against a daily target. */
data class DailyGoalProgress(
    val xpToday: Int,
    val targetXp: Int
) {
    val isMet: Boolean get() = xpToday >= targetXp
    val ratio: Float get() = if (targetXp <= 0) 1f else (xpToday.toFloat() / targetXp).coerceIn(0f, 1f)
}

/**
 * Calculates today's XP using the same 04:00 local-day boundary as streaks.
 */
class DefaultDailyGoalTracker(
    val defaultTargetXp: Int = DEFAULT_TARGET_XP
) : DailyGoalTracker {

    init {
        require(defaultTargetXp >= 0) { "The daily XP target cannot be negative" }
    }

    override fun isGoalMet(xpToday: Int, targetXp: Int): Boolean = xpToday >= targetXp

    override fun progressRatio(xpToday: Int, targetXp: Int): Float =
        if (targetXp <= 0) 1f else (xpToday.toFloat() / targetXp).coerceIn(0f, 1f)

    /** Sums exercise XP whose effective local date is today's effective date. */
    fun xpToday(
        results: Collection<ExerciseResult>,
        now: Instant,
        timeZone: TimeZone
    ): Int {
        val today = effectiveLocalDate(now, timeZone)
        return results
            .asSequence()
            .filter { it.epochMillis <= now.toEpochMilliseconds() }
            .filter { effectiveLocalDate(Instant.fromEpochMilliseconds(it.epochMillis), timeZone) == today }
            .sumOf { it.xpAwarded.coerceAtLeast(0) }
    }

    /** Alias for callers that name the operation explicitly. */
    fun calculateXpToday(
        results: Collection<ExerciseResult>,
        now: Instant,
        timeZone: TimeZone
    ): Int = xpToday(results, now, timeZone)

    /** Builds a progress snapshot for the effective local day. */
    fun progress(
        results: Collection<ExerciseResult>,
        now: Instant,
        timeZone: TimeZone,
        targetXp: Int = defaultTargetXp
    ): DailyGoalProgress {
        require(targetXp >= 0) { "The daily XP target cannot be negative" }
        return DailyGoalProgress(xpToday(results, now, timeZone), targetXp)
    }

    companion object {
        const val DEFAULT_TARGET_XP: Int = 50
    }
}

/** Compatibility name for callers that use the conventional implementation suffix. */
typealias DailyGoalTrackerImpl = DefaultDailyGoalTracker
