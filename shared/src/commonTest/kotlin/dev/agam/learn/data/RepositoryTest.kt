package dev.agam.learn.data

import dev.agam.learn.ai.AiProvider
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryTest {
    @Test
    fun progressQueriesFilterAndObserveByScope() = runTest {
        val driver = createInMemoryTestDriver()
        try {
            val repository = SqlDelightProgressRepository(LearnDatabase(driver))
            repository.recordExerciseResult(
                ExerciseResult(
                    exerciseId = "exercise-1",
                    correct = true,
                    xpAwarded = 10,
                    epochMillis = 100,
                    lessonId = "lesson-1",
                    chapterId = "chapter-1"
                )
            )
            repository.recordExerciseResult(
                ExerciseResult(
                    exerciseId = "exercise-2",
                    correct = false,
                    xpAwarded = 0,
                    epochMillis = 200,
                    lessonId = "lesson-2",
                    chapterId = "chapter-1"
                )
            )

            assertEquals(1, repository.getExerciseResultsForExercise("exercise-1").size)
            assertEquals(1, repository.getExerciseResultsForLesson("lesson-2").size)
            assertEquals(2, repository.getExerciseResultsForChapter("chapter-1").size)
            assertEquals(2, repository.observeExerciseResults().first().size)
        } finally {
            driver.close()
        }
    }

    @Test
    fun dailyXpReviewsAndLessonCompletionPersist() = runTest {
        val driver = createInMemoryTestDriver()
        try {
            val repository = SqlDelightProgressRepository(LearnDatabase(driver))
            repository.recordDailyActivity(DailyActivity(10, 30, 2, false))
            repository.recordDailyActivity(DailyActivity(11, 50, 3, true))
            assertEquals(50, repository.getXpTotalForDay(11))
            assertEquals(mapOf(10L to 30, 11L to 50), repository.getXpTotalsByDay())

            repository.upsertReviewCard(ReviewCard("card-1", box = 2, dueEpochDay = 11))
            repository.upsertReviewCard(ReviewCard("card-2", box = 1, dueEpochDay = 12))
            assertEquals(listOf("card-1"), repository.getDueReviewCards(11).map { it.itemId })

            assertFalse(repository.isLessonCompleted("lesson-1"))
            repository.markLessonCompleted("lesson-1", completedAtEpochMillis = 123)
            assertTrue(repository.isLessonCompleted("lesson-1"))
            assertEquals(setOf("lesson-1"), repository.getCompletedLessonIds())
            assertTrue(repository.observeLessonCompleted("lesson-1").first())
        } finally {
            driver.close()
        }
    }

    @Test
    fun settingsApplyDefaultsAndPersistUpdates() = runTest {
        val driver = createInMemoryTestDriver()
        try {
            val repository = SqlDelightSettingsRepository(LearnDatabase(driver))
            assertEquals(AppSettings(), repository.getSettings())

            repository.setDailyGoalXp(75)
            repository.setAiProvider(AiProvider.OPENAI, "gpt-test")
            repository.setCompilerServer("http://localhost:8080", "token")
            repository.setInterviewMode(true)
            repository.setSoundEffectsEnabled(false)
            repository.setHapticsEnabled(false)
            repository.setReducedMotionEnabled(true)

            assertEquals(
                AppSettings(
                    dailyGoalXp = 75,
                    selectedAiProvider = AiProvider.OPENAI,
                    selectedAiModel = "gpt-test",
                    compilerServerUrl = "http://localhost:8080",
                    compilerServerToken = "token",
                    interviewMode = true,
                    soundEffectsEnabled = false,
                    hapticsEnabled = false,
                    reducedMotionEnabled = true
                ),
                repository.observeSettings().first()
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun openRouterProviderAndCustomModelPersist() = runTest {
        val driver = createInMemoryTestDriver()
        try {
            val repository = SqlDelightSettingsRepository(LearnDatabase(driver))
            repository.setAiProvider(AiProvider.OPENROUTER, "anthropic/claude-sonnet-4.5")

            assertEquals(
                AppSettings(
                    selectedAiProvider = AiProvider.OPENROUTER,
                    selectedAiModel = "anthropic/claude-sonnet-4.5"
                ),
                repository.getSettings()
            )
            assertEquals(repository.getSettings(), repository.observeSettings().first())
        } finally {
            driver.close()
        }
    }

    @Test
    fun progressRepositoryRejectsSecretApiKeySetting() = runTest {
        val driver = createInMemoryTestDriver()
        try {
            val repository = SqlDelightProgressRepository(LearnDatabase(driver))
            assertFailsWith<IllegalArgumentException> {
                repository.setSetting(AI_API_KEY_SETTING, "plain-text-secret")
            }
            assertFailsWith<IllegalArgumentException> {
                repository.getSetting(AI_API_KEY_SETTING)
            }
        } finally {
            driver.close()
        }
    }

}
