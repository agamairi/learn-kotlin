package dev.agam.learn.fake

import dev.agam.learn.domain.DailyGoalTracker

/**
 * Tracks daily XP progress towards user-configured daily goals.
 */
class FakeDailyGoalTracker : DailyGoalTracker {

    override fun isGoalMet(xpToday: Int, targetXp: Int): Boolean {
        return xpToday >= targetXp
    }

    override fun progressRatio(xpToday: Int, targetXp: Int): Float {
        if (targetXp <= 0) return 1f
        return (xpToday.toFloat() / targetXp).coerceIn(0f, 1f)
    }
}
