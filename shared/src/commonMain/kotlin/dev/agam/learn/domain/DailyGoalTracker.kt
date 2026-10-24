package dev.agam.learn.domain

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
