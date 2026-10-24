package dev.agam.learn.ui.lesson

import dev.agam.learn.fake.FakeAnswerGrader
import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FakeXpPolicy
import dev.agam.learn.fake.SampleCourseData
import dev.agam.learn.runtime.kotlin.KotlinLanguageSupport
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
class LessonViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var courseRepository: FakeCourseRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var answerGrader: FakeAnswerGrader
    private lateinit var xpPolicy: FakeXpPolicy
    private lateinit var languageSupport: KotlinLanguageSupport
    private lateinit var viewModel: LessonViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        courseRepository = FakeCourseRepository(listOf(SampleCourseData.defaultCourse))
        progressRepository = FakeProgressRepository()
        answerGrader = FakeAnswerGrader()
        xpPolicy = FakeXpPolicy()
        languageSupport = KotlinLanguageSupport()

        viewModel = LessonViewModel(
            courseRepository = courseRepository,
            progressRepository = progressRepository,
            answerGrader = answerGrader,
            xpPolicy = xpPolicy,
            languageSupport = languageSupport
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCompleteLessonFlow() = runTest(testDispatcher) {
        // 1. Initial Load -> Explanation Step
        viewModel.loadLesson("lesson-01-syntax")
        advanceUntilIdle()

        val state1 = viewModel.uiState.value
        assertEquals("Kotlin Syntax Essentials", state1.lessonTitle)
        assertTrue("Initial step should be explanation", state1.step is LessonStep.Explanation)

        // 2. Start Exercises -> ExercisePlaying (Card 1: MultipleChoice)
        viewModel.onStartPractice()
        advanceUntilIdle()

        val state2 = viewModel.uiState.value
        val step1 = state2.step as LessonStep.ExercisePlaying
        assertEquals(0, step1.exerciseIndex)
        assertEquals(5, step1.totalExercises)
        assertFalse("Cannot check without selecting an option", state2.canCheckAnswer)

        // Select correct option ("val" at index 0)
        viewModel.onOptionSelected(0)
        assertTrue("Can check once option selected", viewModel.uiState.value.canCheckAnswer)

        viewModel.onCheckAnswer()
        advanceUntilIdle()

        val checkedState1 = viewModel.uiState.value
        assertTrue("Answer is checked", checkedState1.isAnswerChecked)
        assertEquals(true, checkedState1.isAnswerCorrect)
        assertEquals(10, checkedState1.xpEarnedInLesson)

        // 3. Continue to Card 2: Cloze
        viewModel.onContinueNext()
        advanceUntilIdle()

        val step2 = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        assertEquals(1, step2.exerciseIndex)
        assertFalse(viewModel.uiState.value.isAnswerChecked)

        // Fill in blank
        viewModel.onClozeAnswerChanged("____1", "\$name")
        assertTrue("Can check filled cloze", viewModel.uiState.value.canCheckAnswer)

        viewModel.onCheckAnswer()
        advanceUntilIdle()

        val checkedState2 = viewModel.uiState.value
        assertEquals(true, checkedState2.isAnswerCorrect)
        assertEquals(20, checkedState2.xpEarnedInLesson)

        // 4. Continue to Card 3: PredictOutput
        viewModel.onContinueNext()
        advanceUntilIdle()

        val step3 = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        assertEquals(2, step3.exerciseIndex)

        // Enter predicted output
        viewModel.onPredictOutputChanged("Sum is 15")
        assertTrue(viewModel.uiState.value.canCheckAnswer)

        viewModel.onCheckAnswer()
        advanceUntilIdle()

        val checkedState3 = viewModel.uiState.value
        assertEquals(true, checkedState3.isAnswerCorrect)
        assertEquals(30, checkedState3.xpEarnedInLesson)

        // 5. Complete remaining exercises (WriteFunction and MultiPartProblem)
        viewModel.onContinueNext()
        advanceUntilIdle()
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        viewModel.onContinueNext()
        advanceUntilIdle()
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        // 6. Complete Lesson -> Summary Step
        viewModel.onContinueNext()
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        val summaryStep = finalState.step as LessonStep.Completed
        assertTrue("All 5 exercises completed", summaryStep.totalCount == 5)
        assertEquals(5, summaryStep.correctCount)
        assertTrue("Earned XP recorded", summaryStep.totalXp > 0)

        // 7. Verify daily activity persisted to repository
        val activities = progressRepository.getDailyActivities()
        assertEquals(1, activities.size)
        assertEquals(summaryStep.totalXp, activities[0].xpEarned)
        assertEquals(5, activities[0].exercisesCompleted)
    }
}
