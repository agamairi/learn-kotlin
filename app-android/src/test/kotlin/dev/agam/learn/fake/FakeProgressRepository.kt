package dev.agam.learn.fake

import dev.agam.learn.data.ProgressCommit
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory implementation of [ProgressRepository] for unit tests.
 */
class FakeProgressRepository : ProgressRepository {

    private val mutex = Mutex()
    private val exerciseResultsState = MutableStateFlow<List<ExerciseResult>>(emptyList())
    private val dailyActivitiesState = MutableStateFlow<Map<Long, DailyActivity>>(emptyMap())
    private val reviewCardsState = MutableStateFlow<Map<String, ReviewCard>>(emptyMap())
    private val lessonCompletionsState = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val settings = mutableMapOf<String, String>()

    override suspend fun recordExerciseResult(result: ExerciseResult) {
        mutex.withLock {
            exerciseResultsState.value = exerciseResultsState.value + result
        }
    }

    override suspend fun getExerciseResults(): List<ExerciseResult> = mutex.withLock {
        exerciseResultsState.value.toList()
    }

    override suspend fun getExerciseResultsForExercise(exerciseId: String): List<ExerciseResult> = mutex.withLock {
        exerciseResultsState.value.filter { it.exerciseId == exerciseId }
    }

    override suspend fun getExerciseResultsForLesson(lessonId: String): List<ExerciseResult> = mutex.withLock {
        exerciseResultsState.value.filter { it.lessonId == lessonId }
    }

    override suspend fun getExerciseResultsForChapter(chapterId: String): List<ExerciseResult> = mutex.withLock {
        exerciseResultsState.value.filter { it.chapterId == chapterId }
    }

    override fun observeExerciseResults(): Flow<List<ExerciseResult>> =
        exerciseResultsState.asStateFlow()

    override fun observeExerciseResultsForExercise(exerciseId: String): Flow<List<ExerciseResult>> =
        exerciseResultsState.map { results -> results.filter { it.exerciseId == exerciseId } }

    override fun observeExerciseResultsForLesson(lessonId: String): Flow<List<ExerciseResult>> =
        exerciseResultsState.map { results -> results.filter { it.lessonId == lessonId } }

    override fun observeExerciseResultsForChapter(chapterId: String): Flow<List<ExerciseResult>> =
        exerciseResultsState.map { results -> results.filter { it.chapterId == chapterId } }

    override suspend fun recordDailyActivity(activity: DailyActivity) {
        mutex.withLock {
            val updated = dailyActivitiesState.value.toMutableMap()
            updated[activity.epochDay] = activity
            dailyActivitiesState.value = updated
        }
    }

    override suspend fun getDailyActivities(): List<DailyActivity> = mutex.withLock {
        dailyActivitiesState.value.values.sortedBy { it.epochDay }
    }

    override suspend fun getDailyActivity(epochDay: Long): DailyActivity? = mutex.withLock {
        dailyActivitiesState.value[epochDay]
    }

    override suspend fun getXpTotalForDay(epochDay: Long): Int = mutex.withLock {
        dailyActivitiesState.value[epochDay]?.xpEarned ?: 0
    }

    override suspend fun getXpTotalsByDay(): Map<Long, Int> = mutex.withLock {
        dailyActivitiesState.value.mapValues { it.value.xpEarned }
    }

    override fun observeXpTotalsByDay(): Flow<Map<Long, Int>> =
        dailyActivitiesState.map { activities -> activities.mapValues { it.value.xpEarned } }

    override suspend fun upsertReviewCard(card: ReviewCard) {
        mutex.withLock {
            val updated = reviewCardsState.value.toMutableMap()
            updated[card.itemId] = card
            reviewCardsState.value = updated
        }
    }

    override suspend fun getAllReviewCards(): List<ReviewCard> = mutex.withLock {
        reviewCardsState.value.values.toList()
    }

    override suspend fun getDueReviewCards(epochDay: Long): List<ReviewCard> = mutex.withLock {
        reviewCardsState.value.values.filter { it.dueEpochDay <= epochDay }
    }

    override fun observeAllReviewCards(): Flow<List<ReviewCard>> =
        reviewCardsState.map { it.values.toList() }

    override fun observeDueReviewCards(epochDay: Long): Flow<List<ReviewCard>> =
        reviewCardsState.map { cards -> cards.values.filter { it.dueEpochDay <= epochDay } }

    override suspend fun getReviewCard(itemId: String): ReviewCard? = mutex.withLock {
        reviewCardsState.value[itemId]
    }

    override suspend fun markLessonCompleted(lessonId: String, completedAtEpochMillis: Long) {
        mutex.withLock {
            val updated = lessonCompletionsState.value.toMutableMap()
            updated[lessonId] = completedAtEpochMillis
            lessonCompletionsState.value = updated
        }
    }

    override suspend fun isLessonCompleted(lessonId: String): Boolean = mutex.withLock {
        lessonCompletionsState.value.containsKey(lessonId)
    }

    override suspend fun getCompletedLessonIds(): Set<String> = mutex.withLock {
        lessonCompletionsState.value.keys.toSet()
    }

    override fun observeLessonCompleted(lessonId: String): Flow<Boolean> =
        lessonCompletionsState.map { it.containsKey(lessonId) }

    override fun observeCompletedLessonIds(): Flow<Set<String>> =
        lessonCompletionsState.map { it.keys.toSet() }

    private val frozenEpochDays = mutableSetOf<Long>()
    private var heldStreakFreezes = 0
    private val committedAttemptIds = mutableSetOf<String>()

    override suspend fun commitProgress(commit: ProgressCommit): Boolean = mutex.withLock {
        if (!committedAttemptIds.add(commit.attemptId)) return@withLock false
        exerciseResultsState.value = exerciseResultsState.value + commit.result
        val updatedActivities = dailyActivitiesState.value.toMutableMap()
        updatedActivities[commit.dailyActivity.epochDay] = commit.dailyActivity
        dailyActivitiesState.value = updatedActivities
        commit.reviewCard?.let { card ->
            val updatedCards = reviewCardsState.value.toMutableMap()
            updatedCards[card.itemId] = card
            reviewCardsState.value = updatedCards
        }
        frozenEpochDays.addAll(commit.frozenEpochDays)
        commit.heldStreakFreezes?.let { heldStreakFreezes = it.coerceIn(0, 2) }
        true
    }

    override suspend fun getFrozenEpochDays(): Set<Long> = mutex.withLock {
        frozenEpochDays.toSet()
    }

    override suspend fun recordStreakFreeze(epochDay: Long) {
        mutex.withLock {
            frozenEpochDays.add(epochDay)
        }
    }

    override suspend fun getHeldStreakFreezes(): Int = mutex.withLock {
        heldStreakFreezes
    }

    override suspend fun setHeldStreakFreezes(count: Int) {
        mutex.withLock {
            heldStreakFreezes = count.coerceIn(0, 2)
        }
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
