package dev.agam.learn.fake

import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory implementation of [ProgressRepository].
 */
class FakeProgressRepository : ProgressRepository {

    private val mutex = Mutex()
    private val exerciseResults = mutableListOf<ExerciseResult>()
    private val dailyActivities = mutableMapOf<Long, DailyActivity>()
    private val reviewCards = mutableMapOf<String, ReviewCard>()
    private val settings = mutableMapOf<String, String>()

    override suspend fun recordExerciseResult(result: ExerciseResult) {
        mutex.withLock {
            exerciseResults.add(result)
        }
    }

    override suspend fun getExerciseResults(): List<ExerciseResult> = mutex.withLock {
        exerciseResults.toList()
    }

    override suspend fun getExerciseResultsForExercise(exerciseId: String): List<ExerciseResult> = mutex.withLock {
        exerciseResults.filter { it.exerciseId == exerciseId }
    }

    override suspend fun recordDailyActivity(activity: DailyActivity) {
        mutex.withLock {
            dailyActivities[activity.epochDay] = activity
        }
    }

    override suspend fun getDailyActivities(): List<DailyActivity> = mutex.withLock {
        dailyActivities.values.sortedBy { it.epochDay }
    }

    override suspend fun getDailyActivity(epochDay: Long): DailyActivity? = mutex.withLock {
        dailyActivities[epochDay]
    }

    override suspend fun upsertReviewCard(card: ReviewCard) {
        mutex.withLock {
            reviewCards[card.itemId] = card
        }
    }

    override suspend fun getAllReviewCards(): List<ReviewCard> = mutex.withLock {
        reviewCards.values.toList()
    }

    override suspend fun getDueReviewCards(epochDay: Long): List<ReviewCard> = mutex.withLock {
        reviewCards.values.filter { it.dueEpochDay <= epochDay }
    }

    override suspend fun getReviewCard(itemId: String): ReviewCard? = mutex.withLock {
        reviewCards[itemId]
    }

    override suspend fun getSetting(key: String): String? = mutex.withLock {
        settings[key]
    }

    override suspend fun setSetting(key: String, value: String) {
        mutex.withLock {
            settings[key] = value
        }
    }
}
