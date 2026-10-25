package dev.agam.learn.ui.lesson

import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.AppSettings
import dev.agam.learn.data.ExerciseAttempt
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.domain.DefaultXpPolicy
import dev.agam.learn.domain.LeitnerScheduler
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
import dev.agam.learn.model.Blank
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runtime.kotlin.KotlinLanguageSupport
import dev.agam.learn.ui.review.ReviewViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        aiTutor: AiTutor? = null,
        isInterviewMode: Boolean = false,
        hasApiKey: Boolean = false
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
            languageSupport = languageSupport,
            aiTutor = aiTutor,
            isInterviewMode = isInterviewMode,
            hasApiKey = hasApiKey
        )
    }

    /** A course with one lesson containing exactly [exercises]. */
    private fun courseWith(vararg exercises: Exercise): Course = courseOf(
        *exercises,
        lessonId = "lesson-studio",
        chapterId = "ch-studio"
    )

    private fun multipleChoice(id: String) = Exercise.MultipleChoice(
        id = id,
        prompt = "Choose the correct answer",
        options = listOf("correct", "incorrect"),
        correctIndex = 0,
        explanation = ""
    )

    private fun cloze(id: String) = Exercise.Cloze(
        id = id,
        prompt = "Fill the blank",
        template = "val answer = ____1",
        blanks = listOf(Blank("____1", listOf("yes"))),
        explanation = ""
    )

    private fun predictOutput(id: String) = Exercise.PredictOutput(
        id = id,
        prompt = "Predict the output",
        code = "println(\"done\")",
        expectedOutput = "done",
        explanation = ""
    )

    @Test
    fun missedCardsAreDeferredThenReviewedInOrderUntilMasteredOnce() = runTest(testDispatcher) {
        val first = multipleChoice("first")
        val second = cloze("second")
        val third = predictOutput("third")
        val vm = viewModelFor(courseWith(first, second, third))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()

        // A Check before an answer and a Continue before checking cannot skip the first card.
        vm.onContinueNext()
        assertEquals("first", (vm.uiState.value.step as LessonStep.ExercisePlaying).exercise.id)
        assertFalse(vm.uiState.value.isAnswerChecked)

        // Initial pass: miss first, master second, miss third. Duplicate taps record/advance once.
        vm.onOptionSelected(1)
        vm.onCheckAnswer()
        vm.onCheckAnswer()
        vm.onContinueNext()
        vm.onContinueNext()
        assertEquals("second", (vm.uiState.value.step as LessonStep.ExercisePlaying).exercise.id)

        vm.onClozeAnswerChanged("____1", "yes")
        vm.onCheckAnswer()
        advanceUntilIdle()
        vm.onContinueNext()
        vm.onPredictOutputChanged("wrong")
        vm.onCheckAnswer()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.correctExercisesCount)
        assertEquals(2, vm.uiState.value.pendingMistakeCount)
        assertFalse(progressRepository.isLessonCompleted("lesson-studio"))
        vm.onContinueNext()

        val intro = vm.uiState.value.step as LessonStep.MistakeReviewIntro
        assertEquals(2, intro.pendingCount)
        assertEquals(2, vm.uiState.value.pendingMistakeCount)
        assertEquals(3, vm.uiState.value.totalExercisesCount)
        assertEquals(1, vm.uiState.value.correctExercisesCount)
        assertFalse(progressRepository.isLessonCompleted("lesson-studio"))

        // Review order starts with first; a repeated wrong answer rotates it behind third.
        vm.startMistakeReview()
        vm.startMistakeReview() // idempotent after the intro is consumed
        assertTrue(vm.uiState.value.isMistakeReview)
        assertEquals("first", (vm.uiState.value.step as LessonStep.ExercisePlaying).exercise.id)
        vm.onOptionSelected(1)
        vm.onCheckAnswer()
        vm.onCheckAnswer()
        advanceUntilIdle()
        vm.onContinueNext()
        vm.onContinueNext()
        assertEquals("third", (vm.uiState.value.step as LessonStep.ExercisePlaying).exercise.id)
        assertEquals(2, vm.uiState.value.pendingMistakeCount)

        // Third is wrong again and rotates behind first; then both recover exactly once.
        vm.onPredictOutputChanged("still wrong")
        vm.onCheckAnswer()
        advanceUntilIdle()
        vm.onContinueNext()
        assertEquals("first", (vm.uiState.value.step as LessonStep.ExercisePlaying).exercise.id)
        vm.onOptionSelected(0)
        vm.onCheckAnswer()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.correctExercisesCount)
        vm.onContinueNext()
        assertEquals("third", (vm.uiState.value.step as LessonStep.ExercisePlaying).exercise.id)
        assertEquals(1, vm.uiState.value.pendingMistakeCount)
        vm.onPredictOutputChanged("done")
        vm.onCheckAnswer()
        advanceUntilIdle()
        vm.onContinueNext()
        advanceUntilIdle()

        val completed = vm.uiState.value.step as LessonStep.Completed
        assertEquals(3, completed.correctCount)
        assertEquals(3, completed.totalCount)
        assertEquals(30, completed.totalXp)
        assertEquals(30, vm.uiState.value.xpEarnedInLesson)
        assertEquals(0, vm.uiState.value.pendingMistakeCount)
        assertFalse(vm.uiState.value.isMistakeReview)
        assertTrue(progressRepository.isLessonCompleted("lesson-studio"))

        val attempts = progressRepository.getExerciseResults()
        assertEquals("Every presentation records once, including wrong retries", 7, attempts.size)
        assertEquals(30, attempts.sumOf { it.xpAwarded })
        assertEquals(3, attempts.count { it.correct })
        assertEquals(3, attempts.map { it.exerciseId }.distinct().size)
        assertTrue(attempts.any { it.exerciseId == "first" && it.correct && it.xpAwarded == 10 })
        val dailyActivity = progressRepository.getDailyActivities().single()
        assertEquals(3, dailyActivity.exercisesCompleted)
        assertEquals(30, dailyActivity.xpEarned)
    }

    @Test
    fun allCorrectOriginalPassCompletesWithoutMistakeReview() = runTest(testDispatcher) {
        val onlyQuestion = multipleChoice("only")
        val vm = viewModelFor(courseWith(onlyQuestion))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        vm.onOptionSelected(0)
        vm.onCheckAnswer()
        advanceUntilIdle()

        vm.onContinueNext()
        vm.onContinueNext()
        advanceUntilIdle()

        val completed = vm.uiState.value.step as LessonStep.Completed
        assertEquals(1, completed.correctCount)
        assertEquals(1, completed.totalCount)
        assertEquals(10, completed.totalXp)
        assertEquals(1, progressRepository.getExerciseResults().size)
        assertTrue(progressRepository.isLessonCompleted("lesson-studio"))
    }

    @Test
    fun reloadingSameLessonDuringMistakeReviewKeepsSessionState() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(multipleChoice("mastered"), cloze("missed")))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()

        vm.onOptionSelected(0)
        vm.onCheckAnswer()
        advanceUntilIdle()
        vm.onContinueNext()
        vm.onClozeAnswerChanged("____1", "wrong")
        vm.onCheckAnswer()
        advanceUntilIdle()
        vm.onContinueNext()
        vm.startMistakeReview()

        val beforeReload = vm.uiState.value
        assertTrue(beforeReload.isMistakeReview)
        assertEquals("missed", (beforeReload.step as LessonStep.ExercisePlaying).exercise.id)
        assertEquals(1, beforeReload.pendingMistakeCount)
        assertEquals(1, beforeReload.correctExercisesCount)
        assertEquals(10, beforeReload.xpEarnedInLesson)

        // LessonScreen calls loadLesson again after recreation; an already-loaded lesson must retain its run.
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()

        val afterReload = vm.uiState.value
        assertEquals(beforeReload.step, afterReload.step)
        assertTrue(afterReload.isMistakeReview)
        assertEquals(1, afterReload.pendingMistakeCount)
        assertEquals(1, afterReload.correctExercisesCount)
        assertEquals(10, afterReload.xpEarnedInLesson)
    }

    @Test
    fun loadingLessonResetsMistakeQueueAndUniqueMasteryCount() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(multipleChoice("only")))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        vm.onOptionSelected(1)
        vm.onCheckAnswer()
        advanceUntilIdle()
        vm.onContinueNext()
        assertTrue(vm.uiState.value.step is LessonStep.MistakeReviewIntro)

        vm.loadLesson("lesson-studio", forceReload = true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.step is LessonStep.Explanation)
        assertEquals(0, vm.uiState.value.pendingMistakeCount)
        assertEquals(0, vm.uiState.value.correctExercisesCount)
        assertFalse(vm.uiState.value.isMistakeReview)
    }

    @Test
    fun staleLessonLoadCannotReplaceTheRunRequestedWhileItWasSuspended() = runTest(testDispatcher) {
        val original = courseOf(multipleChoice("old-card"), lessonId = "lesson-old")
        val chapter = original.chapters.single()
        val newLesson = chapter.lessons.single().copy(id = "lesson-new", title = "New lesson")
        val course = original.copy(chapters = listOf(chapter.copy(lessons = chapter.lessons + newLesson)))
        val firstLoadEntered = CompletableDeferred<Unit>()
        val releaseFirstLoad = CompletableDeferred<Unit>()
        var shouldBlockFirstLoad = true
        val courses = object : CourseRepository {
            override suspend fun getCourse(courseId: String): Course? = course.takeIf { it.id == courseId }

            override suspend fun listCourses(): List<Course> {
                if (shouldBlockFirstLoad) {
                    shouldBlockFirstLoad = false
                    firstLoadEntered.complete(Unit)
                    releaseFirstLoad.await()
                }
                return listOf(course)
            }
        }
        val stagedRecorder = ProgressRecorder(
            progressRepository = progressRepository,
            courseRepository = courses,
            settingsRepository = FakeSettingsRepository(),
            reviewScheduler = LeitnerScheduler(),
            xpPolicy = DefaultXpPolicy(),
            clock = clock,
            timeZone = toronto
        )
        val vm = LessonViewModel(
            courseRepository = courses,
            progressRepository = progressRepository,
            progressRecorder = stagedRecorder,
            answerGrader = answerGrader,
            languageSupport = languageSupport
        )

        vm.loadLesson("lesson-old")
        advanceUntilIdle()
        firstLoadEntered.await()
        vm.loadLesson("lesson-new", forceReload = true)
        advanceUntilIdle()
        assertEquals("lesson-new", vm.uiState.value.lessonId)

        releaseFirstLoad.complete(Unit)
        advanceUntilIdle()

        assertEquals("lesson-new", vm.uiState.value.lessonId)
        assertEquals("New lesson", vm.uiState.value.lessonTitle)
        assertTrue(vm.uiState.value.step is LessonStep.Explanation)
    }

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
        recorder.record(ExerciseAttempt(newAttemptId(), drillStep.exercise.id, correct = true, lessonRunId = viewModel.lessonRunId))
        viewModel.onStudioExerciseCompleted(drillStep.exercise.id, viewModel.lessonRunId)
        advanceUntilIdle()
        viewModel.onContinueNext()
        advanceUntilIdle()

        val problemStep = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        assertTrue(problemStep.exercise is Exercise.MultiPartProblem)
        recorder.record(ExerciseAttempt(newAttemptId(), problemStep.exercise.id, correct = true, lessonRunId = viewModel.lessonRunId))
        viewModel.onStudioExerciseCompleted(problemStep.exercise.id, viewModel.lessonRunId)
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
        val card = multipleChoice("metadata-card")
        val vm = viewModelFor(courseWith(card))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()
        vm.onOptionSelected(0)
        vm.onCheckAnswer()
        advanceUntilIdle()
        assertTrue("Checked attempts remain staged until lesson completion", progressRepository.getExerciseResults().isEmpty())
        vm.onContinueNext()
        advanceUntilIdle()

        val stored = progressRepository.getExerciseResults().single()
        assertEquals("lesson-studio", stored.lessonId)
        assertEquals("ch-studio", stored.chapterId)
    }

    @Test
    fun aMissedLessonCardEntersTheReviewQueueForTomorrow() = runTest(testDispatcher) {
        val missed = multipleChoice("missed-card")
        val vm = viewModelFor(courseWith(missed))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()
        vm.onOptionSelected(1) // wrong
        vm.onCheckAnswer()
        advanceUntilIdle()

        assertNull("A review card is provisional until the lesson settles", progressRepository.getReviewCard(missed.id))
        vm.onContinueNext()
        vm.startMistakeReview()
        vm.onOptionSelected(0)
        vm.onCheckAnswer()
        advanceUntilIdle()
        vm.onContinueNext()
        advanceUntilIdle()

        val card = progressRepository.getReviewCard(missed.id)
        assertEquals(1, card?.box)
        assertEquals(recorder.today() + 1, card?.dueEpochDay)

        // Tomorrow the review queue offers the card.
        clock.instant = Instant.parse("2026-01-07T17:00:00Z")
        val review = ReviewViewModel(
            progressRepository = progressRepository,
            progressRecorder = recorder,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        assertTrue(review.uiState.value.dueCards.any { it.itemId == missed.id })
    }

    @Test
    fun aCardCheckedTwiceIsRecordedOnce() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(multipleChoice("only-card")))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()
        vm.onOptionSelected(0)

        vm.onCheckAnswer()
        vm.onCheckAnswer()
        advanceUntilIdle()

        assertTrue(progressRepository.getExerciseResults().isEmpty())
        assertEquals(10, vm.uiState.value.xpEarnedInLesson)
        vm.onContinueNext()
        advanceUntilIdle()
        assertEquals(1, progressRepository.getExerciseResults().size)
        assertEquals(10, progressRepository.getDailyActivities().single().xpEarned)
    }

    @Test
    fun aCompletedLessonIsSavedAndItsXpIsShownInTheSummary() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(drill()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()
        recorder.record(ExerciseAttempt(newAttemptId(), "drill-sum", correct = true, lessonRunId = vm.lessonRunId))
        vm.onStudioExerciseCompleted("drill-sum", vm.lessonRunId)
        advanceUntilIdle()

        vm.onContinueNext()
        advanceUntilIdle()

        assertTrue(progressRepository.isLessonCompleted("lesson-studio"))
        assertEquals(25, (vm.uiState.value.step as LessonStep.Completed).totalXp)
    }

    @Test
    fun aFailedSettlementKeepsTheRunAndCanBeRetriedWithoutDoubleAward() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(multipleChoice("only-card")))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        vm.onOptionSelected(0)
        vm.onCheckAnswer()
        advanceUntilIdle()
        progressRepository.settlementFailure = IllegalStateException("temporary storage failure")

        vm.onContinueNext()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.step is LessonStep.SettlementFailed)
        assertEquals(10, vm.uiState.value.xpEarnedInLesson)
        assertTrue(progressRepository.getExerciseResults().isEmpty())
        assertTrue(progressRepository.getDailyActivities().isEmpty())

        progressRepository.settlementFailure = null
        vm.retrySettlement()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.step is LessonStep.Completed)
        assertEquals(10, (vm.uiState.value.step as LessonStep.Completed).totalXp)
        assertEquals(1, progressRepository.getExerciseResults().size)
        assertEquals(10, progressRepository.getDailyActivities().single().xpEarned)
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
        assertEquals("code_exercise/drill-sum?lessonRunId=${vm.lessonRunId}", vm.routeForExercise(step.exercise))
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
        assertEquals("problem/problem-ledger?lessonRunId=${vm.lessonRunId}", vm.routeForExercise(step.exercise))
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
        recorder.record(ExerciseAttempt(newAttemptId(), "drill-sum", correct = true, lessonRunId = vm.lessonRunId))

        vm.onStudioExerciseCompleted("drill-sum", "stale-lesson-run")
        advanceUntilIdle()
        assertFalse("A studio callback from another run cannot advance this lesson", vm.uiState.value.isAnswerChecked)

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
        recorder.record(ExerciseAttempt(newAttemptId(), "drill-sum", correct = true, lessonRunId = vm.lessonRunId))
        vm.onStudioExerciseCompleted("drill-sum", vm.lessonRunId)
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

    @Test
    fun moreLikeThisPracticeSheetIsClearlyLabeledAiGeneratedUnverified() = runTest(testDispatcher) {
        val fakeTutor = FakeAiTutor()
        val vm = viewModelFor(
            course = SampleCourseData.defaultCourse,
            aiTutor = fakeTutor,
            isInterviewMode = false,
            hasApiKey = true
        )
        vm.loadLesson("lesson-01-syntax")
        advanceUntilIdle()

        vm.requestMoreLikeThis()
        advanceUntilIdle()

        val practice = vm.uiState.value.aiPracticeState
        assertTrue("Practice sheet should be visible", practice.isVisible)
        assertEquals(3, practice.cards.size)
        assertEquals("AI-generated, unverified", practice.label)
        assertEquals(1, fakeTutor.generateCardsCalls)
    }

    @Test
    fun moreLikeThisCardsAwardNoXpAndDoNotRecordProgress() = runTest(testDispatcher) {
        val fakeTutor = FakeAiTutor()
        val vm = viewModelFor(
            course = SampleCourseData.defaultCourse,
            aiTutor = fakeTutor,
            isInterviewMode = false,
            hasApiKey = true
        )
        vm.loadLesson("lesson-01-syntax")
        advanceUntilIdle()

        vm.requestMoreLikeThis()
        advanceUntilIdle()

        // Card 1: Select correct option (index 0)
        vm.onAiPracticeOptionSelected(0)
        vm.checkAiPracticeAnswer()

        assertTrue(vm.uiState.value.aiPracticeState.isAnswerChecked)
        assertTrue(vm.uiState.value.aiPracticeState.isAnswerCorrect == true)

        // Advance to next card
        vm.nextAiPracticeCard()
        assertEquals(1, vm.uiState.value.aiPracticeState.currentCardIndex)

        // Card 2: Select incorrect option (index 0, correct is 1)
        vm.onAiPracticeOptionSelected(0)
        vm.checkAiPracticeAnswer()

        assertTrue(vm.uiState.value.aiPracticeState.isAnswerChecked)
        assertFalse(vm.uiState.value.aiPracticeState.isAnswerCorrect == true)

        // Check XP in lesson - must still be 0!
        assertEquals("AI practice must award no XP", 0, vm.uiState.value.xpEarnedInLesson)

        // Progress repository must have 0 exercise results
        assertTrue("AI practice must not record progress results", progressRepository.getExerciseResults().isEmpty())

        // Review card must NOT be seeded for incorrect AI practice card
        assertNull("AI practice must not seed review cards", progressRepository.getReviewCard("ai-mc-2"))
    }

    @Test
    fun moreLikeThisIsRefusedInInterviewMode() = runTest(testDispatcher) {
        val fakeTutor = FakeAiTutor()
        val vm = viewModelFor(
            course = SampleCourseData.defaultCourse,
            aiTutor = fakeTutor,
            isInterviewMode = true,
            hasApiKey = true
        )
        vm.loadLesson("lesson-01-syntax")
        advanceUntilIdle()

        vm.requestMoreLikeThis()
        advanceUntilIdle()

        assertFalse("Practice sheet must not be shown in interview mode", vm.uiState.value.aiPracticeState.isVisible)
        assertEquals("Tutor must not be called in interview mode", 0, fakeTutor.generateCardsCalls)
    }

    @Test
    fun moreLikeThisShowsAddKeyMessageWhenNoApiKey() = runTest(testDispatcher) {
        val fakeTutor = FakeAiTutor()
        val vm = viewModelFor(
            course = SampleCourseData.defaultCourse,
            aiTutor = fakeTutor,
            isInterviewMode = false,
            hasApiKey = false
        )
        vm.loadLesson("lesson-01-syntax")
        advanceUntilIdle()

        vm.requestMoreLikeThis()
        advanceUntilIdle()

        val practice = vm.uiState.value.aiPracticeState
        assertTrue(practice.isVisible)
        assertEquals("Add your API key in Settings", practice.errorMessage)
        assertEquals(0, fakeTutor.generateCardsCalls)
    }

    @Test
    fun consecutiveStreakIncrementsAndTriggersMilestoneAtMultiplesOfFive() = runTest(testDispatcher) {
        viewModel.loadLesson("lesson-01-syntax")
        advanceUntilIdle()
        viewModel.onStartPractice()
        advanceUntilIdle()

        // Initial streak is 0
        assertEquals(0, viewModel.uiState.value.consecutiveCorrectStreak)
        assertNull(viewModel.uiState.value.activeMilestone)

        // Question 1: correct -> streak = 1
        viewModel.onOptionSelected(0)
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.consecutiveCorrectStreak)
        assertEquals(1, viewModel.uiState.value.consecutiveStreak)
        assertNull(viewModel.uiState.value.activeMilestone)
        val event1 = viewModel.uiState.value.feedbackEvent
        assertEquals(true, event1?.correct)
        assertEquals(false, event1?.isMilestone)
        assertEquals(1, event1?.streak)

        // Acknowledge event 1
        viewModel.acknowledgeFeedbackEvent(event1!!.id)
        assertNull(viewModel.uiState.value.feedbackEvent)

        // Question 2: correct -> streak = 2
        viewModel.onContinueNext()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.consecutiveCorrectStreak) // streak retained across continue
        viewModel.onClozeAnswerChanged("____1", "\$name")
        viewModel.onCheckAnswer()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.consecutiveCorrectStreak)

        // Question 3: correct -> streak = 3
        viewModel.onContinueNext()
        advanceUntilIdle()
        viewModel.onPredictOutputChanged("Sum is 15")
        viewModel.onCheckAnswer()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.consecutiveCorrectStreak)

        // Question 4: studio drill pass -> streak = 4
        viewModel.onContinueNext()
        advanceUntilIdle()
        val drillStep = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        recorder.record(ExerciseAttempt(newAttemptId(), drillStep.exercise.id, correct = true, lessonRunId = viewModel.lessonRunId))
        viewModel.onStudioExerciseCompleted(drillStep.exercise.id, viewModel.lessonRunId)
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.consecutiveCorrectStreak)
        assertNull(viewModel.uiState.value.activeMilestone)

        // Question 5: studio problem pass -> streak = 5 -> MILESTONE!
        viewModel.onContinueNext()
        advanceUntilIdle()
        val problemStep = viewModel.uiState.value.step as LessonStep.ExercisePlaying
        recorder.record(ExerciseAttempt(newAttemptId(), problemStep.exercise.id, correct = true, lessonRunId = viewModel.lessonRunId))
        viewModel.onStudioExerciseCompleted(problemStep.exercise.id, viewModel.lessonRunId)
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.consecutiveCorrectStreak)
        val milestone = viewModel.uiState.value.activeMilestone
        assertTrue("Milestone should be triggered at 5 consecutive correct", milestone != null)
        assertEquals(5, milestone?.streakCount)

        val milestoneEvent = viewModel.uiState.value.feedbackEvent
        assertTrue("Feedback event should report milestone fanfare", milestoneEvent?.isMilestone == true)
        assertEquals(5, milestoneEvent?.streak)

        // A stale screen callback cannot consume a newer one-shot presentation event.
        val milestoneId = milestone!!.id
        viewModel.acknowledgeMilestonePresentation(milestoneId + 1)
        assertEquals(milestone, viewModel.uiState.value.activeMilestone)

        // The screen consumes the event as soon as it captures it for local animation.
        viewModel.acknowledgeMilestonePresentation(milestoneId)
        assertNull(viewModel.uiState.value.activeMilestone)
    }

    @Test
    fun consecutiveStreakResetsToZeroOnWrongAnswer() = runTest(testDispatcher) {
        viewModel.loadLesson("lesson-01-syntax")
        advanceUntilIdle()
        viewModel.onStartPractice()
        advanceUntilIdle()

        // Answer 1: correct -> streak = 1
        viewModel.onOptionSelected(0)
        viewModel.onCheckAnswer()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.consecutiveCorrectStreak)

        // Answer 2: wrong -> streak resets to 0
        viewModel.onContinueNext()
        advanceUntilIdle()
        viewModel.onClozeAnswerChanged("____1", "wrong_answer")
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.consecutiveCorrectStreak)
        assertNull(viewModel.uiState.value.activeMilestone)
        val wrongEvent = viewModel.uiState.value.feedbackEvent
        assertEquals(false, wrongEvent?.correct)
        assertEquals(false, wrongEvent?.isMilestone)
        assertEquals(0, wrongEvent?.streak)

        // Answer 3: correct -> streak restarts at 1
        viewModel.onContinueNext()
        advanceUntilIdle()
        viewModel.onPredictOutputChanged("Sum is 15")
        viewModel.onCheckAnswer()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.consecutiveCorrectStreak)
    }

    @Test
    fun forceReloadingLessonResetsStreakAndMilestone() = runTest(testDispatcher) {
        viewModel.loadLesson("lesson-01-syntax")
        advanceUntilIdle()
        viewModel.onStartPractice()
        advanceUntilIdle()

        viewModel.onOptionSelected(0)
        viewModel.onCheckAnswer()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.consecutiveCorrectStreak)

        // An explicit reload starts a fresh run and resets the session streak.
        viewModel.loadLesson("lesson-01-syntax", forceReload = true)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.consecutiveCorrectStreak)
        assertNull(viewModel.uiState.value.activeMilestone)
        assertNull(viewModel.uiState.value.feedbackEvent)
    }

    @Test
    fun studioExercisePassIncrementsStreakOnlyOnceAndAwardsNoExtraXp() = runTest(testDispatcher) {
        val vm = viewModelFor(courseWith(drill()))
        vm.loadLesson("lesson-studio")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()

        recorder.record(ExerciseAttempt(newAttemptId(), "drill-sum", correct = true, lessonRunId = vm.lessonRunId))
        vm.onStudioExerciseCompleted("drill-sum", vm.lessonRunId)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.consecutiveCorrectStreak)
        assertEquals(25, vm.uiState.value.xpEarnedInLesson)

        // Call again for the same exercise
        vm.onStudioExerciseCompleted("drill-sum")
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.consecutiveCorrectStreak)
        assertEquals(25, vm.uiState.value.xpEarnedInLesson)
    }

    @Test
    fun aiPracticeDoesNotAffectConsecutiveStreakOrTriggerMilestone() = runTest(testDispatcher) {
        val fakeTutor = FakeAiTutor()
        val vm = viewModelFor(
            course = SampleCourseData.defaultCourse,
            aiTutor = fakeTutor,
            isInterviewMode = false,
            hasApiKey = true
        )
        vm.loadLesson("lesson-01-syntax")
        advanceUntilIdle()
        vm.onStartPractice()
        advanceUntilIdle()

        // 1 real correct answer -> streak = 1
        vm.onOptionSelected(0)
        vm.onCheckAnswer()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.consecutiveCorrectStreak)

        // Open AI practice
        vm.requestMoreLikeThis()
        advanceUntilIdle()

        // Answer AI practice card correctly
        vm.onAiPracticeOptionSelected(0)
        vm.checkAiPracticeAnswer()
        advanceUntilIdle()

        // Real session streak remains untouched!
        assertEquals("AI practice must not change real lesson streak", 1, vm.uiState.value.consecutiveCorrectStreak)
        assertNull("AI practice must never trigger milestone celebration", vm.uiState.value.activeMilestone)
        assertEquals("AI practice awards no XP", 10, vm.uiState.value.xpEarnedInLesson)
    }

    @Test
    fun continueNextDismissesActiveMilestoneSafely() = runTest(testDispatcher) {
        viewModel.loadLesson("lesson-01-syntax")
        advanceUntilIdle()
        viewModel.onStartPractice()
        advanceUntilIdle()

        // Complete 5 questions
        viewModel.onOptionSelected(0)
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        viewModel.onContinueNext()
        advanceUntilIdle()
        viewModel.onClozeAnswerChanged("____1", "\$name")
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        viewModel.onContinueNext()
        advanceUntilIdle()
        viewModel.onPredictOutputChanged("Sum is 15")
        viewModel.onCheckAnswer()
        advanceUntilIdle()

        viewModel.onContinueNext()
        advanceUntilIdle()
        val drill = (viewModel.uiState.value.step as LessonStep.ExercisePlaying).exercise
        recorder.record(ExerciseAttempt(newAttemptId(), drill.id, correct = true, lessonRunId = viewModel.lessonRunId))
        viewModel.onStudioExerciseCompleted(drill.id, viewModel.lessonRunId)
        advanceUntilIdle()

        viewModel.onContinueNext()
        advanceUntilIdle()
        val problem = (viewModel.uiState.value.step as LessonStep.ExercisePlaying).exercise
        recorder.record(ExerciseAttempt(newAttemptId(), problem.id, correct = true, lessonRunId = viewModel.lessonRunId))
        viewModel.onStudioExerciseCompleted(problem.id, viewModel.lessonRunId)
        advanceUntilIdle()

        // Milestone is present at 5
        assertTrue(viewModel.uiState.value.activeMilestone != null)

        // Continuing to summary clears active milestone
        viewModel.onContinueNext()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeMilestone)
        assertTrue(viewModel.uiState.value.step is LessonStep.Completed)
    }

    private class FakeAiTutor : AiTutor {
        var generateCardsResult: AiResult<List<Exercise>> = AiResult.Success(
            listOf(
                Exercise.MultipleChoice(
                    id = "ai-mc-1",
                    prompt = "AI prompt 1",
                    options = listOf("opt1", "opt2"),
                    correctIndex = 0,
                    explanation = "Explanation 1",
                    aiGenerated = true
                ),
                Exercise.MultipleChoice(
                    id = "ai-mc-2",
                    prompt = "AI prompt 2",
                    options = listOf("optA", "optB"),
                    correctIndex = 1,
                    explanation = "Explanation 2",
                    aiGenerated = true
                ),
                Exercise.Cloze(
                    id = "ai-cloze-1",
                    prompt = "AI prompt 3",
                    template = "val x = ____",
                    blanks = listOf(dev.agam.learn.model.Blank("____", listOf("10"))),
                    explanation = "Explanation 3",
                    aiGenerated = true
                )
            )
        )
        var generateCardsCalls = 0

        override suspend fun explainMistake(
            exercise: Exercise,
            userAnswer: String,
            expectedResult: String
        ): AiResult<String> = AiResult.Success("Explanation")

        override suspend fun hint(exercise: Exercise, level: Int): AiResult<String> =
            AiResult.Success("Hint")

        override suspend fun generateCards(topic: String, count: Int): AiResult<List<Exercise>> {
            generateCardsCalls++
            return generateCardsResult
        }

        override suspend fun reviewCode(exercise: Exercise, code: String): AiResult<String> =
            AiResult.Success("Review")
    }
}
