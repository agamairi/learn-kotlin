package dev.agam.learn.ui.lesson

import dev.agam.learn.data.AppSettings
import dev.agam.learn.fake.FakeAnswerGrader
import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FakeSettingsRepository
import dev.agam.learn.fake.FakeXpPolicy
import dev.agam.learn.fake.SampleCourseData
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runner.LanguageId
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var answerGrader: FakeAnswerGrader
    private lateinit var xpPolicy: FakeXpPolicy
    private lateinit var languageSupport: KotlinLanguageSupport
    private lateinit var viewModel: LessonViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        progressRepository = FakeProgressRepository()
        answerGrader = FakeAnswerGrader()
        xpPolicy = FakeXpPolicy()
        languageSupport = KotlinLanguageSupport()

        viewModel = viewModelFor(SampleCourseData.defaultCourse)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelFor(
        course: Course,
        settings: FakeSettingsRepository = FakeSettingsRepository()
    ) = LessonViewModel(
        courseRepository = FakeCourseRepository(listOf(course)),
        progressRepository = progressRepository,
        settingsRepository = settings,
        answerGrader = answerGrader,
        xpPolicy = xpPolicy,
        languageSupport = languageSupport
    )

    /** A course with one lesson containing exactly [exercises]. */
    private fun courseWith(vararg exercises: Exercise): Course = Course(
        id = "kotlin-stripe",
        language = LanguageId.KOTLIN,
        title = "Studio course",
        description = "Lesson routing fixtures",
        chapters = listOf(
            Chapter(
                id = "ch-studio",
                title = "Studio",
                summary = "Studio exercises",
                lessons = listOf(
                    Lesson(
                        id = "lesson-studio",
                        title = "Studio lesson",
                        explanation = "Explanation",
                        exercises = exercises.toList()
                    )
                )
            )
        )
    )

    private fun drill() = Exercise.WriteFunction(
        id = "drill-sum",
        prompt = "Write sum(a, b)",
        starterCode = "fun sum(a: Int, b: Int): Int = TODO()",
        hiddenTests = "",
        referenceSolution = "fun sum(a: Int, b: Int): Int = a + b"
    )

    private fun problem() = Exercise.MultiPartProblem(
        id = "problem-ledger",
        prompt = "Ledger",
        story = "Process a ledger.",
        parts = listOf(
            ProblemPart(
                id = "part1",
                prompt = "parse",
                hiddenTests = "",
                referenceSolution = "",
                expected = ""
            )
        )
    )

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

        // 5. Continue to the studio exercises (code drill, then multi-part problem). They are
        // completed in their own screens and come back as results; Check does nothing for them.
        viewModel.onContinueNext()
        advanceUntilIdle()
        val drillStep = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        assertTrue(drillStep.exercise is Exercise.WriteFunction)
        viewModel.onCheckAnswer()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAnswerChecked)
        viewModel.recordExternalExerciseResult(drillStep.exercise.id, isCorrect = true)
        advanceUntilIdle()
        viewModel.onContinueNext()
        advanceUntilIdle()

        val problemStep = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        assertTrue(problemStep.exercise is Exercise.MultiPartProblem)
        viewModel.recordExternalExerciseResult(problemStep.exercise.id, isCorrect = true)
        advanceUntilIdle()
        viewModel.onContinueNext()
        advanceUntilIdle()

        // 6. Complete Lesson -> Summary Step
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

    @Test
    fun writeFunctionExerciseRoutesToCodeExerciseScreen() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(drill()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()

        val step = vm.uiState.value.step as LessonStep.ExercisePlaying
        assertTrue(step.exercise is Exercise.WriteFunction)
        assertEquals("code_exercise/drill-sum", vm.routeForExercise(step.exercise))
        assertTrue(vm.uiState.value.isStudioExercise)
        assertFalse("A code drill has no Check action in the lesson", vm.uiState.value.canCheckAnswer)
    }

    @Test
    fun multiPartProblemRoutesToProblemScreen() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(problem()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()

        val step = vm.uiState.value.step as LessonStep.ExercisePlaying
        assertTrue(step.exercise is Exercise.MultiPartProblem)
        assertEquals("problem/problem-ledger", vm.routeForExercise(step.exercise))
        assertTrue(vm.uiState.value.isStudioExercise)
    }

    @Test
    fun checkCannotPassAStudioExerciseWithoutTheStudio() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(drill()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()

        vm.onCheckAnswer()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAnswerChecked)
        assertEquals(0, vm.uiState.value.xpEarnedInLesson)
        assertTrue(progressRepository.getExerciseResults().isEmpty())
    }

    @Test
    fun studioResultIsRecordedOnceWithTheLessonId() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(drill()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()

        vm.recordExternalExerciseResult("drill-sum", isCorrect = true)
        advanceUntilIdle()
        vm.recordExternalExerciseResult("drill-sum", isCorrect = true)
        advanceUntilIdle()

        val results = progressRepository.getExerciseResults()
        assertEquals(1, results.size)
        assertEquals("drill-sum", results[0].exerciseId)
        assertEquals("lesson-studio", results[0].lessonId)
        assertEquals(25, results[0].xpAwarded)
        assertTrue(vm.uiState.value.isAnswerChecked)
        assertEquals(true, vm.uiState.value.isAnswerCorrect)
    }

    @Test
    fun lessonCompletionUsesTheDailyGoalFromSettings() = runTest(testDispatcher) {
        // The drill awards 25 XP, so a goal of 20 is met and the default goal of 50 is not.
        val vm = viewModelFor(
            courseWith(drill()),
            settings = FakeSettingsRepository(AppSettings(dailyGoalXp = 20))
        )
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()
        vm.recordExternalExerciseResult("drill-sum", isCorrect = true)
        advanceUntilIdle()
        vm.onContinueNext()
        advanceUntilIdle()

        val activity = progressRepository.getDailyActivities().single()
        assertEquals(25, activity.xpEarned)
        assertTrue("goal from settings (20) is met by 25 XP", activity.goalMet)
    }

    @Test
    fun lessonGradedExercisesHaveNoStudioRoute() {
        val multipleChoice = SampleCourseData.defaultCourse.chapters.first().lessons.first().exercises
            .first { it is Exercise.MultipleChoice }

        assertNull(viewModel.routeForExercise(multipleChoice))
    }
}
