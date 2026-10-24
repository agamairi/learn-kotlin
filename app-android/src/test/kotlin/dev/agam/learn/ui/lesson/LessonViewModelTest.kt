package dev.agam.learn.ui.lesson

import dev.agam.learn.data.AppSettings
import dev.agam.learn.data.ExerciseAttempt
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.newAttemptId
import dev.agam.learn.fake.FakeAnswerGrader
import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FakeSettingsRepository
import dev.agam.learn.fake.FixedClock
import dev.agam.learn.fake.SampleCourseData
import dev.agam.learn.fake.courseOf
import dev.agam.learn.fake.progressRecorderFor
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runtime.kotlin.KotlinLanguageSupport
import dev.agam.learn.ui.review.ReviewViewModel
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val toronto = TimeZone.of("America/Toronto")
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var answerGrader: FakeAnswerGrader
    private lateinit var clock: FixedClock
    private lateinit var recorder: ProgressRecorder
    private lateinit var languageSupport: KotlinLanguageSupport
    private lateinit var viewModel: LessonViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        progressRepository = FakeProgressRepository()
        answerGrader = FakeAnswerGrader()
        languageSupport = KotlinLanguageSupport()
        clock = FixedClock(Instant.parse("2026-01-06T17:00:00Z")) // noon in Toronto on Jan 6

        viewModel = viewModelFor(SampleCourseData.defaultCourse)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelFor(
        course: Course,
        settings: FakeSettingsRepository = FakeSettingsRepository()
    ): LessonViewModel {
        recorder = progressRecorderFor(
            progressRepository,
            listOf(course),
            settings = settings,
            clock = clock,
            timeZone = toronto
        )
        return LessonViewModel(
            courseRepository = FakeCourseRepository(listOf(course)),
            progressRepository = progressRepository,
            progressRecorder = recorder,
            answerGrader = answerGrader,
            languageSupport = languageSupport
        )
    }

    /** A course with one lesson containing exactly [exercises]. */
    private fun courseWith(vararg exercises: Exercise): Course = courseOf(
        *exercises,
        lessonId = "lesson-studio",
        chapterId = "ch-studio"
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
        // completed in their own screens, which record the attempt; Check does nothing for them.
        viewModel.onContinueNext()
        advanceUntilIdle()
        val drillStep = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        assertTrue(drillStep.exercise is Exercise.WriteFunction)
        viewModel.onCheckAnswer()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAnswerChecked)
        recorder.record(ExerciseAttempt(newAttemptId(), drillStep.exercise.id, correct = true))
        viewModel.onStudioExerciseCompleted(drillStep.exercise.id)
        advanceUntilIdle()
        viewModel.onContinueNext()
        advanceUntilIdle()

        val problemStep = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        assertTrue(problemStep.exercise is Exercise.MultiPartProblem)
        recorder.record(ExerciseAttempt(newAttemptId(), problemStep.exercise.id, correct = true))
        viewModel.onStudioExerciseCompleted(problemStep.exercise.id)
        advanceUntilIdle()
        viewModel.onContinueNext()
        advanceUntilIdle()

        // 6. Complete Lesson -> Summary Step
        val finalState = viewModel.uiState.value
        val summaryStep = finalState.step as LessonStep.Completed
        assertTrue("All 5 exercises completed", summaryStep.totalCount == 5)
        assertEquals(5, summaryStep.correctCount)
        assertEquals(30 + 25, summaryStep.totalXp)

        // 7. The daily activity was written by the recorder: XP matches and every exercise counted once
        val activities = progressRepository.getDailyActivities()
        assertEquals(1, activities.size)
        assertEquals(summaryStep.totalXp, activities[0].xpEarned)
        assertEquals(5, activities[0].exercisesCompleted)

        // 8. The finished lesson is saved as complete
        assertTrue(progressRepository.isLessonCompleted("lesson-01-syntax"))
    }

    @Test
    fun resultsCarryTheLessonAndChapterIds() = runTest(testDispatcher) {
        viewModel.loadLesson("lesson-01-syntax")
        advanceUntilIdle()
        viewModel.onStartPractice()
        advanceUntilIdle()
        viewModel.onOptionSelected(0)
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        val stored = progressRepository.getExerciseResults().single()
        assertEquals("lesson-01-syntax", stored.lessonId)
        assertEquals(SampleCourseData.defaultCourse.chapters.first().id, stored.chapterId)
    }

    @Test
    fun aMissedLessonCardEntersTheReviewQueueForTomorrow() = runTest(testDispatcher) {
        viewModel.loadLesson("lesson-01-syntax")
        advanceUntilIdle()
        viewModel.onStartPractice()
        advanceUntilIdle()
        viewModel.onOptionSelected(1) // wrong
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        val missed = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        val card = progressRepository.getReviewCard(missed.exercise.id)
        assertEquals(1, card?.box)
        assertEquals(recorder.today() + 1, card?.dueEpochDay)

        // Tomorrow the review queue offers the card.
        clock.instant = Instant.parse("2026-01-07T17:00:00Z")
        val review = ReviewViewModel(
            progressRepository = progressRepository,
            progressRecorder = recorder,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        assertTrue(review.uiState.value.dueCards.any { it.itemId == missed.exercise.id })
    }

    @Test
    fun aCardCheckedTwiceIsRecordedOnce() = runTest(testDispatcher) {
        viewModel.loadLesson("lesson-01-syntax")
        advanceUntilIdle()
        viewModel.onStartPractice()
        advanceUntilIdle()
        viewModel.onOptionSelected(0)

        viewModel.onCheckAnswer()
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        assertEquals(1, progressRepository.getExerciseResults().size)
        assertEquals(10, viewModel.uiState.value.xpEarnedInLesson)
    }

    @Test
    fun aCompletedLessonIsSavedAndItsXpIsShownInTheSummary() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(drill()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()
        recorder.record(ExerciseAttempt(newAttemptId(), "drill-sum", correct = true))
        vm.onStudioExerciseCompleted("drill-sum")
        advanceUntilIdle()

        vm.onContinueNext()
        advanceUntilIdle()

        assertTrue(progressRepository.isLessonCompleted("lesson-studio"))
        assertEquals(25, (vm.uiState.value.step as LessonStep.Completed).totalXp)
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
    fun aStudioReturnWithoutAStoredPassDoesNotAdvanceTheLesson() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(drill()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()

        vm.onStudioExerciseCompleted("drill-sum")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAnswerChecked)
        assertTrue(progressRepository.getExerciseResults().isEmpty())
    }

    @Test
    fun studioReturnAdvancesOnceWithTheXpItRecorded() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(drill()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()
        recorder.record(ExerciseAttempt(newAttemptId(), "drill-sum", correct = true))

        vm.onStudioExerciseCompleted("drill-sum")
        advanceUntilIdle()
        vm.onStudioExerciseCompleted("drill-sum")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAnswerChecked)
        assertEquals(true, vm.uiState.value.isAnswerCorrect)
        assertEquals(25, vm.uiState.value.xpEarnedInLesson)
        assertEquals(1, vm.uiState.value.correctExercisesCount)
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
        recorder.record(ExerciseAttempt(newAttemptId(), "drill-sum", correct = true))
        vm.onStudioExerciseCompleted("drill-sum")
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
