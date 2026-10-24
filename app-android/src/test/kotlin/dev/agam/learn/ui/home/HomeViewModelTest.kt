package dev.agam.learn.ui.home

import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeDailyGoalTracker
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FakeStreakCalculator
import dev.agam.learn.fake.SampleCourseData
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    private lateinit var courseRepository: FakeCourseRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var streakCalculator: FakeStreakCalculator
    private lateinit var dailyGoalTracker: FakeDailyGoalTracker
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        courseRepository = FakeCourseRepository(listOf(SampleCourseData.defaultCourse))
        progressRepository = FakeProgressRepository()
        streakCalculator = FakeStreakCalculator()
        dailyGoalTracker = FakeDailyGoalTracker()

        viewModel = HomeViewModel(
            courseRepository = courseRepository,
            progressRepository = progressRepository,
            streakCalculator = streakCalculator,
            dailyGoalTracker = dailyGoalTracker
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialHomeState() = runTest(testDispatcher) {
        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.streakDays)
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
        val now = Clock.System.now()
        val todayEpochDay = now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()

        progressRepository.recordDailyActivity(
            DailyActivity(
                epochDay = todayEpochDay,
                xpEarned = 50,
                exercisesCompleted = 5,
                goalMet = true
            )
        )

        progressRepository.recordExerciseResult(
            ExerciseResult(
                exerciseId = "mc-val-immutability",
                correct = true,
                xpAwarded = 10,
                epochMillis = now.toEpochMilliseconds()
            )
        )

        progressRepository.upsertReviewCard(
            ReviewCard(
                itemId = "card-1",
                box = 1,
                dueEpochDay = todayEpochDay
            )
        )

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
}
