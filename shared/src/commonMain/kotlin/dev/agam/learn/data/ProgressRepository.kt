package dev.agam.learn.data

import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard

/**
 * Local persistent storage interface for learner progress, spaced repetition cards, and settings.
 */
interface ProgressRepository {
    /**
     * Records an individual exercise attempt.
     */
    suspend fun recordExerciseResult(result: ExerciseResult)

    /**
     * Retrieves all recorded exercise attempts.
     */
    suspend fun getExerciseResults(): List<ExerciseResult>

    /**
     * Retrieves history for a specific exercise.
     */
    suspend fun getExerciseResultsForExercise(exerciseId: String): List<ExerciseResult>

    /**
     * Records or updates daily aggregated activity metrics.
     */
    suspend fun recordDailyActivity(activity: DailyActivity)

    /**
     * Returns all daily activity entries ordered chronologically.
     */
    suspend fun getDailyActivities(): List<DailyActivity>

    /**
     * Returns activity for a specific day.
     */
    suspend fun getDailyActivity(epochDay: Long): DailyActivity?

    /**
     * Saves or updates a spaced repetition flashcard.
     */
    suspend fun upsertReviewCard(card: ReviewCard)

    /**
     * Returns all flashcards currently in the Leitner system.
     */
    suspend fun getAllReviewCards(): List<ReviewCard>

    /**
     * Returns cards due for review on or before the given epoch day.
     */
    suspend fun getDueReviewCards(epochDay: Long): List<ReviewCard>

    /**
     * Returns a specific review card by item identifier.
     */
    suspend fun getReviewCard(itemId: String): ReviewCard?

    /**
     * Gets a key-value setting.
     */
    suspend fun getSetting(key: String): String?

    /**
     * Sets or updates a key-value setting.
     */
    suspend fun setSetting(key: String, value: String)
}
