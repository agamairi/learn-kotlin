package dev.agam.learn.ui.home

import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeDailyGoalTracker
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FakeSettingsRepository
import dev.agam.learn.fake.FixedClock
import dev.agam.learn.fake.SampleCourseData
import dev.agam.learn.fake.progressRecorderFor
import dev.agam.learn.domain.effectiveLocalDate
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val toronto = TimeZone.of("America/Toronto")
    private lateinit var courseRepository: FakeCourseRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var clock: FixedClock
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        courseRepository = FakeCourseRepository(listOf(SampleCourseData.defaultCourse))
        progressRepository = FakeProgressRepository()
        settings = FakeSettingsRepository()
        clock = FixedClock(Instant.parse("2026-01-06T17:00:00Z")) // 12:00 in Toronto on Jan 6
        viewModel = viewModelWith(clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelWith(clock: FixedClock) = HomeViewModel(
        courseRepository = courseRepository,
        progressRepository = progressRepository,
        settingsRepository = settings,
        progressRecorder = progressRecorderFor(
            progressRepository,
            listOf(SampleCourseData.defaultCourse),
            settings = settings,
            clock = clock,
            timeZone = toronto
        ),
        dailyGoalTracker = FakeDailyGoalTracker()
    )

    private fun todayEpochDay(): Long = effectiveLocalDate(clock.instant, toronto).toEpochDays().toLong()

    @Test
    fun testInitialHomeState() = runTest(testDispatcher) {
        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.streakDays)
        assertEquals(0, state.heldStreakFreezes)
        assertEquals(0, state.todayXp)
        assertEquals(50, state.dailyGoalXp)
        assertEquals(0f, state.goalProgressRatio, 0.001f)
        assertFalse(state.isGoalMet)
        assertEquals(0, state.reviewDueCount)

        assertNotNull("Continue lesson should recommend first lesson", state.continueLesson)
        assertEquals("lesson-01-syntax", state.continueLesson?.lessonId)
    }

    @Test
    fun testHomeStateWithActivityAndReview() = runTest(testDispatcher) {
        val today = todayEpochDay()
        progressRepository.recordDailyActivity(
            DailyActivity(epochDay = today, xpEarned = 50, exercisesCompleted = 5, goalMet = true)
        )
        progressRepository.recordExerciseResult(
            ExerciseResult(
                exerciseId = "mc-val-immutability",
                correct = true,
                xpAwarded = 10,
                epochMillis = clock.instant.toEpochMilliseconds()
            )
        )
        progressRepository.upsertReviewCard(ReviewCard(itemId = "card-1", box = 1, dueEpochDay = today))

        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.streakDays)
        assertTrue(state.isStreakActiveToday)
        assertEquals(50, state.todayXp)
        assertEquals(1.0f, state.goalProgressRatio, 0.001f)
        assertTrue(state.isGoalMet)
        assertEquals(1, state.reviewDueCount)
    }

    @Test
    fun theGoalComesFromSettingsNotFromAFixedFifty() = runTest(testDispatcher) {
        settings.setDailyGoalXp(20)
        progressRepository.recordDailyActivity(
            DailyActivity(epochDay = todayEpochDay(), xpEarned = 25, exercisesCompleted = 1, goalMet = true)
        )

        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(20, state.dailyGoalXp)
        assertTrue(state.isGoalMet)
        assertEquals(1.0f, state.goalProgressRatio, 0.001f)
    }

    @Test
    fun theStreakAndFreezesComeFromTheSharedEvaluation() = runTest(testDispatcher) {
        // Seven goal days (nine to three days ago) earn one freeze. Two days ago was missed and the
        // freeze covers it; yesterday was a goal day. Today is not missed yet.
        settings.setDailyGoalXp(10)
        for (daysAgo in 9 downTo 3) {
            progressRepository.recordDailyActivity(
                DailyActivity(epochDay = todayEpochDay() - daysAgo, xpEarned = 10, exercisesCompleted = 1, goalMet = true)
            )
        }
        progressRepository.recordDailyActivity(
            DailyActivity(epochDay = todayEpochDay() - 1, xpEarned = 10, exercisesCompleted = 1, goalMet = true)
        )

        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("The one freeze was spent covering the missed day", 0, state.heldStreakFreezes)
        assertEquals(9, state.streakDays)
    }
}
