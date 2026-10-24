package dev.agam.learn.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

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
