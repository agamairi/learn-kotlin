package dev.agam.learn.ui.stats

import dev.agam.learn.data.AppSettings
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FakeSettingsRepository
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatsViewModelTest {

    private lateinit var fakeRepo: FakeProgressRepository
    private lateinit var fakeSettings: FakeSettingsRepository
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        fakeRepo = FakeProgressRepository()
        fakeSettings = FakeSettingsRepository(AppSettings(dailyGoalXp = 75))
    }

    @Test
    fun testStatsLoading() = runBlocking {
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)

        listOf(
            DailyActivity(epochDay = today - 2, xpEarned = 50, exercisesCompleted = 5, goalMet = true),
            DailyActivity(epochDay = today - 1, xpEarned = 60, exercisesCompleted = 6, goalMet = true),
            DailyActivity(epochDay = today, xpEarned = 40, exercisesCompleted = 4, goalMet = false)
        ).forEach { fakeRepo.recordDailyActivity(it) }

        listOf(
            ExerciseResult(exerciseId = "ch01-syntax-1", correct = true, xpAwarded = 10, epochMillis = 1000),
            ExerciseResult(exerciseId = "ch01-syntax-2", correct = false, xpAwarded = 0, epochMillis = 2000),
            ExerciseResult(exerciseId = "ch02-strings-1", correct = true, xpAwarded = 10, epochMillis = 3000)
        ).forEach { fakeRepo.recordExerciseResult(it) }

        val viewModel = StatsViewModel(
            progressRepository = fakeRepo,
            settingsRepository = fakeSettings,
            coroutineScope = testScope
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(150, state.totalXp)
        assertEquals(15, state.totalExercisesCompleted)
        assertEquals(7, state.recentXpHistory.size)
        assertEquals(28, state.calendarDays.size)
        assertTrue(state.currentStreak >= 2)
    }

    @Test
    fun noAttemptsMeansNoChapterRowsInsteadOfSamples() = runBlocking {
        val viewModel = StatsViewModel(
            progressRepository = fakeRepo,
            settingsRepository = fakeSettings,
            coroutineScope = testScope
        )

        assertTrue(viewModel.uiState.value.chapterAccuracies.isEmpty())
    }

    @Test
    fun dailyGoalComesFromSavedSettings() = runBlocking {
        val viewModel = StatsViewModel(
            progressRepository = fakeRepo,
            settingsRepository = fakeSettings,
            coroutineScope = testScope
        )

        assertEquals(75, viewModel.uiState.value.dailyGoalXp)
    }
}
