package dev.agam.learn.ui.path

import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.SampleCourseData
import dev.agam.learn.model.ExerciseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PathViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var courseRepository: FakeCourseRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var viewModel: PathViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        courseRepository = FakeCourseRepository(listOf(SampleCourseData.defaultCourse))
        progressRepository = FakeProgressRepository()

        viewModel = PathViewModel(
            courseRepository = courseRepository,
            progressRepository = progressRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialPathLocking() = runTest(testDispatcher) {
        viewModel.loadPath()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Kotlin Foundations", state.courseTitle)
        assertTrue(state.chapters.isNotEmpty())

        val ch1 = state.chapters[0]
        assertEquals("ch01-intro", ch1.id)
        assertTrue("Chapter 1 has at least 2 lessons", ch1.lessons.size >= 2)

        val lesson1 = ch1.lessons[0]
        val lesson2 = ch1.lessons[1]

        assertEquals(LessonStatus.UNLOCKED, lesson1.status)
        assertEquals(LessonStatus.LOCKED, lesson2.status)
        assertEquals(0, state.completedLessons)
    }

    @Test
    fun testCompletingLessonUnlocksNextLesson() = runTest(testDispatcher) {
        // Complete all exercises in lesson 1
        val lesson1Exercises = SampleCourseData.defaultCourse.chapters[0].lessons[0].exercises
        for (exercise in lesson1Exercises) {
            progressRepository.recordExerciseResult(
                ExerciseResult(
                    exerciseId = exercise.id,
                    correct = true,
                    xpAwarded = 10,
                    epochMillis = 1000L
                )
            )
        }

        viewModel.loadPath()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val ch1 = state.chapters[0]
        val lesson1 = ch1.lessons[0]
        val lesson2 = ch1.lessons[1]

        assertEquals(LessonStatus.COMPLETED, lesson1.status)
        assertEquals("Next lesson should unlock", LessonStatus.UNLOCKED, lesson2.status)
        assertEquals(1, state.completedLessons)
        assertTrue("Overall progress updated", state.overallProgressFraction > 0f)
    }
}
