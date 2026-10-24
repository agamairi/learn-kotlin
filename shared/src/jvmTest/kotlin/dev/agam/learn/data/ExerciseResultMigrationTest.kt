package dev.agam.learn.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExerciseResultMigrationTest {

    @Test
    fun aVersionOneDatabaseKeepsItsResultsAndGainsUniqueAttemptIds() = runTest {
        val driver = JdbcSqliteDriver("jdbc:sqlite::memory:")
        try {
            createAlphaOneSchema(driver)
            insertAlphaOneRows(driver)

            LearnDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)

            val database = LearnDatabase(driver)
            assertEveryAlphaOneRowSurvives(database)
            database.streakFreezeQueries.insertStreakFreeze(200L)
            assertEquals(
                listOf(200L),
                database.streakFreezeQueries.selectAllStreakFreezes().executeAsList()
            )

            assertEveryGeneratedSelectCanRun(database)

            val repository = SqlDelightProgressRepository(database)
            assertEquals(2, repository.getExerciseResults().size, "Existing rows survive the migration")

            // Legacy rows have no attempt id, so they never collide with each other.
            val committed = repository.commitProgress(
                ProgressCommit(
                    attemptId = "new-attempt",
                    result = repository.getExerciseResults().first(),
                    dailyActivity = dev.agam.learn.model.DailyActivity(1L, 10, 1, goalMet = false)
                )
            )
            assertEquals(true, committed)
            assertEquals(
                true,
                database.exerciseResultQueries.selectResultIdByAttemptId("new-attempt")
                    .executeAsOneOrNull() != null
            )
            assertFailsWith<Exception> {
                driver.execute(
                    null,
                    "INSERT INTO exercise_result (exercise_id, correct, xp_awarded, epoch_millis, attempt_id) VALUES ('x', 1, 0, 1, 'new-attempt')",
                    0
                )
            }
        } finally {
            driver.close()
        }
    }

    private fun createAlphaOneSchema(driver: JdbcSqliteDriver) {
        listOf(
            """
            CREATE TABLE daily_activity (
                epoch_day INTEGER PRIMARY KEY NOT NULL,
                xp_earned INTEGER NOT NULL,
                exercises_completed INTEGER NOT NULL,
                goal_met INTEGER NOT NULL
            );
            """,
            """
            CREATE TABLE exercise_result (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                exercise_id TEXT NOT NULL,
                lesson_id TEXT,
                chapter_id TEXT,
                correct INTEGER NOT NULL,
                xp_awarded INTEGER NOT NULL,
                epoch_millis INTEGER NOT NULL
            );
            """,
            """
            CREATE TABLE lesson_completion (
                lesson_id TEXT PRIMARY KEY NOT NULL,
                completed_at_epoch_millis INTEGER NOT NULL
            );
            """,
            """
            CREATE TABLE review_card (
                item_id TEXT PRIMARY KEY NOT NULL,
                box INTEGER NOT NULL,
                due_epoch_day INTEGER NOT NULL
            );
            """,
            """
            CREATE TABLE settings_kv (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            );
            """
        ).forEach { statement ->
            driver.execute(null, statement.trimIndent(), 0)
        }
    }

    private fun insertAlphaOneRows(driver: JdbcSqliteDriver) {
        listOf(
            "INSERT INTO daily_activity (epoch_day, xp_earned, exercises_completed, goal_met) VALUES (100, 50, 2, 1);",
            "INSERT INTO exercise_result (exercise_id, lesson_id, chapter_id, correct, xp_awarded, epoch_millis) VALUES ('old-1', 'lesson-1', 'chapter-1', 1, 10, 5);",
            "INSERT INTO exercise_result (exercise_id, lesson_id, chapter_id, correct, xp_awarded, epoch_millis) VALUES ('old-2', 'lesson-2', 'chapter-1', 0, 0, 6);",
            "INSERT INTO lesson_completion (lesson_id, completed_at_epoch_millis) VALUES ('lesson-1', 1234);",
            "INSERT INTO review_card (item_id, box, due_epoch_day) VALUES ('card-1', 2, 101);",
            "INSERT INTO settings_kv (key, value) VALUES ('theme', 'dark');"
        ).forEach { statement ->
            driver.execute(null, statement, 0)
        }
    }

    private fun assertEveryAlphaOneRowSurvives(database: LearnDatabase) {
        val dailyActivity = database.dailyActivityQueries.selectAllDailyActivities().executeAsList().single()
        assertEquals(100L, dailyActivity.epoch_day)
        assertEquals(50L, dailyActivity.xp_earned)
        assertEquals(2L, dailyActivity.exercises_completed)
        assertEquals(1L, dailyActivity.goal_met)

        val exerciseResults = database.exerciseResultQueries.selectAllExerciseResults().executeAsList()
        assertEquals(2, exerciseResults.size)
        assertEquals("old-1", exerciseResults[0].exercise_id)
        assertEquals("lesson-1", exerciseResults[0].lesson_id)
        assertEquals("chapter-1", exerciseResults[0].chapter_id)
        assertEquals(1L, exerciseResults[0].correct)
        assertEquals(10L, exerciseResults[0].xp_awarded)
        assertEquals(5L, exerciseResults[0].epoch_millis)
        assertEquals(null, exerciseResults[0].attempt_id)
        assertEquals("old-2", exerciseResults[1].exercise_id)
        assertEquals(0L, exerciseResults[1].correct)
        assertEquals(0L, exerciseResults[1].xp_awarded)
        assertEquals(6L, exerciseResults[1].epoch_millis)
        assertEquals(null, exerciseResults[1].attempt_id)

        val lessonCompletion = database.lessonCompletionQueries.selectAllLessonCompletions().executeAsList().single()
        assertEquals("lesson-1", lessonCompletion.lesson_id)
        assertEquals(1234L, lessonCompletion.completed_at_epoch_millis)

        val reviewCard = database.reviewCardQueries.selectAllReviewCards().executeAsList().single()
        assertEquals("card-1", reviewCard.item_id)
        assertEquals(2L, reviewCard.box)
        assertEquals(101L, reviewCard.due_epoch_day)

        val setting = database.settingsKvQueries.selectAllSettings().executeAsList().single()
        assertEquals("theme", setting.key)
        assertEquals("dark", setting.value_)
    }

    private fun assertEveryGeneratedSelectCanRun(database: LearnDatabase) {
        val dailyActivity = database.dailyActivityQueries
        assertEquals(listOf(100L to 50L), dailyActivity.selectXpTotalsByDay().executeAsList().map { it.epoch_day to it.xp_earned })
        assertEquals(50L, dailyActivity.selectXpTotalForDay(100L).executeAsOne())
        assertEquals(100L, dailyActivity.selectDailyActivityByDay(100L).executeAsOne().epoch_day)

        val exerciseResults = database.exerciseResultQueries
        assertEquals(1, exerciseResults.selectExerciseResultsForExercise("old-1").executeAsList().size)
        assertEquals(1, exerciseResults.selectExerciseResultsForLesson("lesson-1").executeAsList().size)
        assertEquals(2, exerciseResults.selectExerciseResultsForChapter("chapter-1").executeAsList().size)

        val lessonCompletions = database.lessonCompletionQueries
        assertEquals(true, lessonCompletions.selectLessonCompletionById("lesson-1").executeAsOneOrNull() != null)

        val reviewCards = database.reviewCardQueries
        assertEquals(1, reviewCards.selectDueReviewCards(101L).executeAsList().size)
        assertEquals(true, reviewCards.selectReviewCardById("card-1").executeAsOneOrNull() != null)

        val settings = database.settingsKvQueries
        assertEquals("dark", settings.selectSettingByKey("theme").executeAsOne())

        assertEquals(200L, database.streakFreezeQueries.selectStreakFreezeByDay(200L).executeAsOne())
    }
}
