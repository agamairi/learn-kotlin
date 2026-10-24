package dev.agam.learn.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** SQLDelight-backed implementation of [ProgressRepository]. */
class SqlDelightProgressRepository(
    private val database: LearnDatabase
) : ProgressRepository {

    private val exerciseResults = database.exerciseResultQueries
    private val dailyActivities = database.dailyActivityQueries
    private val reviewCards = database.reviewCardQueries
    private val lessonCompletions = database.lessonCompletionQueries
    private val settings = database.settingsKvQueries

    override suspend fun recordExerciseResult(result: ExerciseResult) {
        exerciseResults.insertExerciseResult(
            exercise_id = result.exerciseId,
            lesson_id = result.lessonId,
            chapter_id = result.chapterId,
            correct = result.correct.toSqlBoolean(),
            xp_awarded = result.xpAwarded.toLong(),
            epoch_millis = result.epochMillis
        )
    }

    override suspend fun getExerciseResults(): List<ExerciseResult> =
        exerciseResults.selectAllExerciseResults().executeAsList().map { row ->
            ExerciseResult(
                exerciseId = row.exercise_id,
                correct = row.correct != 0L,
                xpAwarded = row.xp_awarded.toInt(),
                epochMillis = row.epoch_millis,
                lessonId = row.lesson_id,
                chapterId = row.chapter_id
            )
        }

    override suspend fun getExerciseResultsForExercise(exerciseId: String): List<ExerciseResult> =
        exerciseResults.selectExerciseResultsForExercise(exerciseId)
            .executeAsList()
            .map { row ->
                ExerciseResult(
                    exerciseId = row.exercise_id,
                    correct = row.correct != 0L,
                    xpAwarded = row.xp_awarded.toInt(),
                    epochMillis = row.epoch_millis,
                    lessonId = row.lesson_id,
                    chapterId = row.chapter_id
                )
            }

    override suspend fun getExerciseResultsForLesson(lessonId: String): List<ExerciseResult> =
        exerciseResults.selectExerciseResultsForLesson(lessonId)
            .executeAsList()
            .map { row ->
                ExerciseResult(
                    exerciseId = row.exercise_id,
                    correct = row.correct != 0L,
                    xpAwarded = row.xp_awarded.toInt(),
                    epochMillis = row.epoch_millis,
                    lessonId = row.lesson_id,
                    chapterId = row.chapter_id
                )
            }

    override suspend fun getExerciseResultsForChapter(chapterId: String): List<ExerciseResult> =
        exerciseResults.selectExerciseResultsForChapter(chapterId)
            .executeAsList()
            .map { row ->
                ExerciseResult(
                    exerciseId = row.exercise_id,
                    correct = row.correct != 0L,
                    xpAwarded = row.xp_awarded.toInt(),
                    epochMillis = row.epoch_millis,
                    lessonId = row.lesson_id,
                    chapterId = row.chapter_id
                )
            }

    override fun observeExerciseResults(): Flow<List<ExerciseResult>> =
        exerciseResults.selectAllExerciseResults().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { row ->
                ExerciseResult(
                    exerciseId = row.exercise_id,
                    correct = row.correct != 0L,
                    xpAwarded = row.xp_awarded.toInt(),
                    epochMillis = row.epoch_millis,
                    lessonId = row.lesson_id,
                    chapterId = row.chapter_id
                )
            }
        }

    override fun observeExerciseResultsForExercise(exerciseId: String): Flow<List<ExerciseResult>> =
        exerciseResults.selectExerciseResultsForExercise(exerciseId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
            rows.map { row ->
                ExerciseResult(
                    exerciseId = row.exercise_id,
                    correct = row.correct != 0L,
                    xpAwarded = row.xp_awarded.toInt(),
                    epochMillis = row.epoch_millis,
                    lessonId = row.lesson_id,
                    chapterId = row.chapter_id
                )
            }
        }

    override fun observeExerciseResultsForLesson(lessonId: String): Flow<List<ExerciseResult>> =
        exerciseResults.selectExerciseResultsForLesson(lessonId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
            rows.map { row ->
                ExerciseResult(
                    exerciseId = row.exercise_id,
                    correct = row.correct != 0L,
                    xpAwarded = row.xp_awarded.toInt(),
                    epochMillis = row.epoch_millis,
                    lessonId = row.lesson_id,
                    chapterId = row.chapter_id
                )
            }
        }

    override fun observeExerciseResultsForChapter(chapterId: String): Flow<List<ExerciseResult>> =
        exerciseResults.selectExerciseResultsForChapter(chapterId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
            rows.map { row ->
                ExerciseResult(
                    exerciseId = row.exercise_id,
                    correct = row.correct != 0L,
                    xpAwarded = row.xp_awarded.toInt(),
                    epochMillis = row.epoch_millis,
                    lessonId = row.lesson_id,
                    chapterId = row.chapter_id
                )
            }
        }

    override suspend fun recordDailyActivity(activity: DailyActivity) {
        dailyActivities.insertDailyActivity(
            epoch_day = activity.epochDay,
            xp_earned = activity.xpEarned.toLong(),
            exercises_completed = activity.exercisesCompleted.toLong(),
            goal_met = activity.goalMet.toSqlBoolean()
        )
    }

    override suspend fun getDailyActivities(): List<DailyActivity> =
        dailyActivities.selectAllDailyActivities().executeAsList().map { row ->
            DailyActivity(
                epochDay = row.epoch_day,
                xpEarned = row.xp_earned.toInt(),
                exercisesCompleted = row.exercises_completed.toInt(),
                goalMet = row.goal_met != 0L
            )
        }

    override suspend fun getDailyActivity(epochDay: Long): DailyActivity? =
        dailyActivities.selectDailyActivityByDay(epochDay)
            .executeAsOneOrNull()
            ?.let { row ->
                DailyActivity(
                    epochDay = row.epoch_day,
                    xpEarned = row.xp_earned.toInt(),
                    exercisesCompleted = row.exercises_completed.toInt(),
                    goalMet = row.goal_met != 0L
                )
            }

    override suspend fun getXpTotalForDay(epochDay: Long): Int =
        dailyActivities.selectXpTotalForDay(epochDay).executeAsOne().toInt()

    override suspend fun getXpTotalsByDay(): Map<Long, Int> =
        dailyActivities.selectXpTotalsByDay().executeAsList().associate { row ->
            row.epoch_day to row.xp_earned.toInt()
        }

    override fun observeXpTotalsByDay(): Flow<Map<Long, Int>> =
        dailyActivities.selectXpTotalsByDay().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.associate { row ->
                row.epoch_day to row.xp_earned.toInt()
            }
        }

    override suspend fun upsertReviewCard(card: ReviewCard) {
        reviewCards.insertReviewCard(
            item_id = card.itemId,
            box = card.box.toLong(),
            due_epoch_day = card.dueEpochDay
        )
    }

    override suspend fun getAllReviewCards(): List<ReviewCard> =
        reviewCards.selectAllReviewCards().executeAsList().map { row ->
            ReviewCard(row.item_id, row.box.toInt(), row.due_epoch_day)
        }

    override suspend fun getDueReviewCards(epochDay: Long): List<ReviewCard> =
        reviewCards.selectDueReviewCards(epochDay).executeAsList().map { row ->
            ReviewCard(row.item_id, row.box.toInt(), row.due_epoch_day)
        }

    override fun observeAllReviewCards(): Flow<List<ReviewCard>> =
        reviewCards.selectAllReviewCards().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { row ->
                ReviewCard(row.item_id, row.box.toInt(), row.due_epoch_day)
            }
        }

    override fun observeDueReviewCards(epochDay: Long): Flow<List<ReviewCard>> =
        reviewCards.selectDueReviewCards(epochDay).asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { row ->
                ReviewCard(row.item_id, row.box.toInt(), row.due_epoch_day)
            }
        }

    override suspend fun getReviewCard(itemId: String): ReviewCard? =
        reviewCards.selectReviewCardById(itemId).executeAsOneOrNull()?.let { row ->
            ReviewCard(row.item_id, row.box.toInt(), row.due_epoch_day)
        }

    override suspend fun markLessonCompleted(lessonId: String, completedAtEpochMillis: Long) {
        lessonCompletions.insertLessonCompletion(lessonId, completedAtEpochMillis)
    }

    override suspend fun isLessonCompleted(lessonId: String): Boolean =
        lessonCompletions.selectLessonCompletionById(lessonId).executeAsOneOrNull() != null

    override suspend fun getCompletedLessonIds(): Set<String> =
        lessonCompletions.selectAllLessonCompletions().executeAsList()
            .mapTo(linkedSetOf()) { it.lesson_id }

    override fun observeLessonCompleted(lessonId: String): Flow<Boolean> =
        lessonCompletions.selectLessonCompletionById(lessonId).asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { row -> row != null }

    override fun observeCompletedLessonIds(): Flow<Set<String>> =
        lessonCompletions.selectAllLessonCompletions().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.mapTo(linkedSetOf()) { it.lesson_id }
        }

    override suspend fun getSetting(key: String): String? {
        require(key != AI_API_KEY_SETTING) {
            "Secret settings cannot be accessed via ProgressRepository; use SecretStore instead"
        }
        return settings.selectSettingByKey(key).executeAsOneOrNull()
    }

    override suspend fun setSetting(key: String, value: String) {
        require(key != AI_API_KEY_SETTING) {
            "Secret settings cannot be stored via ProgressRepository; use SecretStore instead"
        }
        settings.insertSetting(key, value)
    }

    private fun Boolean.toSqlBoolean(): Long = if (this) 1L else 0L

}
