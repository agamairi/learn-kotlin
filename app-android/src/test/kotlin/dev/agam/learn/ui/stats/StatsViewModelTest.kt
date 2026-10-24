package dev.agam.learn.ui.stats

import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatsViewModelTest {

    private lateinit var fakeRepo: FakeProgressRepository
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        fakeRepo = FakeProgressRepository()
    }

    @Test
    fun testStatsLoading() {
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)

        fakeRepo.activities = listOf(
            DailyActivity(epochDay = today - 2, xpEarned = 50, exercisesCompleted = 5, goalMet = true),
            DailyActivity(epochDay = today - 1, xpEarned = 60, exercisesCompleted = 6, goalMet = true),
            DailyActivity(epochDay = today, xpEarned = 40, exercisesCompleted = 4, goalMet = false)
        )

        fakeRepo.results = listOf(
            ExerciseResult(exerciseId = "ch01-syntax-1", correct = true, xpAwarded = 10, epochMillis = 1000),
            ExerciseResult(exerciseId = "ch01-syntax-2", correct = false, xpAwarded = 0, epochMillis = 2000),
            ExerciseResult(exerciseId = "ch02-strings-1", correct = true, xpAwarded = 10, epochMillis = 3000)
        )

        fakeRepo.settings["settings.daily_goal"] = "50"

        val viewModel = StatsViewModel(
            progressRepository = fakeRepo,
            coroutineScope = testScope
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(150, state.totalXp)
        assertEquals(15, state.totalExercisesCompleted)
        assertEquals(50, state.dailyGoalXp)
        assertEquals(7, state.recentXpHistory.size)
        assertEquals(28, state.calendarDays.size)
        assertTrue(state.currentStreak >= 2)
    }

    private class FakeProgressRepository : ProgressRepository {
        var activities: List<DailyActivity> = emptyList()
        var results: List<ExerciseResult> = emptyList()
        val settings = mutableMapOf<String, String>()

        override suspend fun recordExerciseResult(result: ExerciseResult) {}
        override suspend fun getExerciseResults(): List<ExerciseResult> = results
        override suspend fun getExerciseResultsForExercise(exerciseId: String): List<ExerciseResult> = emptyList()
        override suspend fun recordDailyActivity(activity: DailyActivity) {}
        override suspend fun getDailyActivities(): List<DailyActivity> = activities
        override suspend fun getDailyActivity(epochDay: Long): DailyActivity? = activities.find { it.epochDay == epochDay }
        override suspend fun upsertReviewCard(card: ReviewCard) {}
        override suspend fun getAllReviewCards(): List<ReviewCard> = emptyList()
        override suspend fun getDueReviewCards(epochDay: Long): List<ReviewCard> = emptyList()
        override suspend fun getReviewCard(itemId: String): ReviewCard? = null
        override suspend fun getSetting(key: String): String? = settings[key]
        override suspend fun setSetting(key: String, value: String) { settings[key] = value }
    }
}
