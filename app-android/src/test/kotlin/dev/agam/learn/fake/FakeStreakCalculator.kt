package dev.agam.learn.fake

import dev.agam.learn.domain.StreakCalculator
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Calculates learner streaks with a 4:00 AM local day rollover boundary.
 *
 * Activities performed before 4:00 AM local time are attributed to the previous calendar day.
 */
class FakeStreakCalculator : StreakCalculator {

    override fun calculateStreak(
        activityTimestamps: Collection<Instant>,
        now: Instant,
        timeZone: TimeZone
    ): Int {
        if (activityTimestamps.isEmpty()) return 0

        fun effectiveLearningDay(instant: Instant): LocalDate {
            val ldt = instant.toLocalDateTime(timeZone)
            return if (ldt.hour < 4) {
                ldt.date.minus(1, DateTimeUnit.DAY)
            } else {
                ldt.date
            }
        }

        val today = effectiveLearningDay(now)
        val activeDays = activityTimestamps.map { effectiveLearningDay(it) }.toSet()

        var currentDay = if (activeDays.contains(today)) {
            today
        } else if (activeDays.contains(today.minus(1, DateTimeUnit.DAY))) {
            today.minus(1, DateTimeUnit.DAY)
        } else {
            return 0
        }

        var streak = 0
        while (activeDays.contains(currentDay)) {
            streak++
            currentDay = currentDay.minus(1, DateTimeUnit.DAY)
        }

        return streak
    }
}
