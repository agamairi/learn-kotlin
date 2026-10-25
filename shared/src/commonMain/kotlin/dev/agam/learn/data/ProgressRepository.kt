package dev.agam.learn.data

import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.flow.Flow

/**
 * Everything one learner event writes, applied in a single database transaction by
 * [ProgressRepository.commitProgress]. Built by [ProgressRecorder]; screens never construct one.
 */
data class ProgressCommit(
    /** Identifies the attempt. A commit whose id was already stored writes nothing. */
    val attemptId: String,
    val result: ExerciseResult,
    /** The full daily activity row for the effective day, after this event. */
    val dailyActivity: DailyActivity,
    /** A review card to insert or replace: a seeded card for a missed lesson card, or a rescheduled review. */
    val reviewCard: ReviewCard? = null,
    /** Streak freezes spent by this event, stored so the streak stays stable. */
    val frozenEpochDays: Set<Long> = emptySet(),
    /** Freezes still held after this event, or null to leave the stored count unchanged. */
    val heldStreakFreezes: Int? = null
)

/** One attempt held until the learner completes the whole lesson run. */
data class StagedLessonAttempt(
    val attemptId: String,
    val result: ExerciseResult,
    val completesExercise: Boolean,
    val reviewCard: ReviewCard? = null
)

/** All durable changes produced by one completed lesson run. */
data class LessonProgressSettlement(
    val runId: String,
    val lessonId: String,
    val completedAtEpochMillis: Long,
    val attempts: List<StagedLessonAttempt>,
    val dailyActivity: DailyActivity?,
    val reviewCards: List<ReviewCard>,
    val frozenEpochDays: Set<Long>,
    val heldStreakFreezes: Int
)

/**
 * Local persistent storage interface for learner progress, spaced repetition cards, and settings.
 */
interface ProgressRepository {
    /**
     * Records an individual exercise attempt.
     */
    suspend fun recordExerciseResult(result: ExerciseResult)

    /**
     * Writes one learner event atomically. Returns false, and writes nothing, when
     * [ProgressCommit.attemptId] was already committed.
     */
    suspend fun commitProgress(commit: ProgressCommit): Boolean

    /**
     * Atomically commits one lesson run and marks the lesson complete. Returns false only when
     * [LessonProgressSettlement.runId] was already settled; attempt id conflicts throw so callers
     * can retain staged progress and offer a retry instead of reporting a false completion.
     */
    suspend fun settleLessonProgress(settlement: LessonProgressSettlement): Boolean

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

    /** Returns all epoch days that were covered by a streak freeze. */
    suspend fun getFrozenEpochDays(): Set<Long>

    /** Records that an epoch day was covered by a streak freeze. */
    suspend fun recordStreakFreeze(epochDay: Long)

    /** Returns the number of currently held streak freezes. */
    suspend fun getHeldStreakFreezes(): Int

    /** Sets the number of held streak freezes. */
    suspend fun setHeldStreakFreezes(count: Int)

    /**
     * Gets a key-value setting.
     */
    suspend fun getSetting(key: String): String?

    /**
     * Sets or updates a key-value setting.
     */
    suspend fun setSetting(key: String, value: String)
}
