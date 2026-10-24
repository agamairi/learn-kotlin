package dev.agam.learn.data

import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.flow.Flow

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

    /** Retrieves attempt history for every exercise in a lesson. */
    suspend fun getExerciseResultsForLesson(lessonId: String): List<ExerciseResult>

    /** Retrieves attempt history for every exercise in a chapter. */
    suspend fun getExerciseResultsForChapter(chapterId: String): List<ExerciseResult>

    /** Observes all exercise attempts as they are recorded. */
    fun observeExerciseResults(): Flow<List<ExerciseResult>>

    /** Observes attempt history for one exercise. */
    fun observeExerciseResultsForExercise(exerciseId: String): Flow<List<ExerciseResult>>

    /** Observes attempt history for one lesson. */
    fun observeExerciseResultsForLesson(lessonId: String): Flow<List<ExerciseResult>>

    /** Observes attempt history for one chapter. */
    fun observeExerciseResultsForChapter(chapterId: String): Flow<List<ExerciseResult>>

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

    /** Returns the XP total stored for a local epoch day. */
    suspend fun getXpTotalForDay(epochDay: Long): Int

    /** Returns XP totals keyed by local epoch day, in chronological order. */
    suspend fun getXpTotalsByDay(): Map<Long, Int>

    /** Alias emphasizing that epoch days are local calendar days chosen by the caller. */
    suspend fun getXpTotalsByLocalDay(): Map<Long, Int> = getXpTotalsByDay()

    /** Observes XP totals keyed by local epoch day. */
    fun observeXpTotalsByDay(): Flow<Map<Long, Int>>

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

    /** Observes all cards currently in the Leitner system. */
    fun observeAllReviewCards(): Flow<List<ReviewCard>>

    /** Observes cards due on or before the given epoch day. */
    fun observeDueReviewCards(epochDay: Long): Flow<List<ReviewCard>>

    /**
     * Returns a specific review card by item identifier.
     */
    suspend fun getReviewCard(itemId: String): ReviewCard?

    /** Marks a lesson as complete. Repeating this operation is idempotent. */
    suspend fun markLessonCompleted(lessonId: String, completedAtEpochMillis: Long)

    /** Returns whether a lesson has been completed. */
    suspend fun isLessonCompleted(lessonId: String): Boolean

    /** Returns the identifiers of all completed lessons. */
    suspend fun getCompletedLessonIds(): Set<String>

    /** Observes the completion state of one lesson. */
    fun observeLessonCompleted(lessonId: String): Flow<Boolean>

    /** Observes all completed lesson identifiers. */
    fun observeCompletedLessonIds(): Flow<Set<String>>

    /**
     * Gets a key-value setting.
     */
    suspend fun getSetting(key: String): String?

    /**
     * Sets or updates a key-value setting.
     */
    suspend fun setSetting(key: String, value: String)
}
