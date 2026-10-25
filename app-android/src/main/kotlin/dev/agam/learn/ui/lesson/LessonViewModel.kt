package dev.agam.learn.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ExerciseAttempt
import dev.agam.learn.data.LessonRunSettlementOutcome
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.data.newAttemptId
import dev.agam.learn.di.AppContainer
import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import dev.agam.learn.runner.LanguageSupport
import dev.agam.learn.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

/**
 * ViewModel managing the state machine for lesson explanation, exercise progression,
 * grading, XP accumulation, and results presentation.
 *
 * Results are staged through [ProgressRecorder] and become durable together when the learner
 * completes the lesson and its end-of-lesson mistake round.
 */
class LessonViewModel(
    private val courseRepository: CourseRepository,
    private val progressRepository: ProgressRepository,
    private val progressRecorder: ProgressRecorder,
    private val answerGrader: AnswerGrader,
    val languageSupport: LanguageSupport,
    private val aiTutor: AiTutor? = null,
    val isInterviewMode: Boolean = false,
    val hasApiKey: Boolean = false
) : ViewModel() {

    constructor(
        appContainer: AppContainer,
        isInterviewMode: Boolean = false,
        hasApiKey: Boolean = false
    ) : this(
        courseRepository = appContainer.courseRepository,
        progressRepository = appContainer.progressRepository,
        progressRecorder = appContainer.progressRecorder,
        answerGrader = appContainer.answerGrader,
        languageSupport = appContainer.languageSupport,
        aiTutor = appContainer.aiTutor,
        isInterviewMode = isInterviewMode,
        hasApiKey = hasApiKey
    )

    private val _uiState = MutableStateFlow(
        LessonUiState(
            isAiAvailable = aiTutor != null,
            isInterviewMode = isInterviewMode,
            hasApiKey = hasApiKey
        )
    )
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private var activeLesson: Lesson? = null
    private var activeCourseRequestId: String? = null
    private var exercises: List<Exercise> = emptyList()
    private val mistakeReviewQueue = mutableListOf<Exercise>()
    private val queuedMistakeIds = mutableSetOf<String>()
    private val masteredExerciseIds = mutableSetOf<String>()
    private var mistakeReviewTotal = 0
    private var mistakeReviewCorrectCount = 0

    /** Identifies this visit to the lesson and scopes attempts from both the player and studios. */
    private var runId: String = newAttemptId()
    val lessonRunId: String get() = runId

    private var nextEventId: Long = 1L
    private fun generateEventId(): Long = nextEventId++
    private var nextPresentationAttemptOrdinal: Long = 1L
    private fun generatePresentationAttemptId(exerciseId: String): String =
        "$runId:$exerciseId:${nextPresentationAttemptOrdinal++}"

    private var pendingRecordCount = 0
    private var pendingRecordsComplete = CompletableDeferred(Unit)
    private var settlementJob: Job? = null
    private var settlementJobRunId: String? = null

    /**
     * Route of the studio screen that completes [exercise], or null for exercises the lesson grades itself.
     */
    fun routeForExercise(exercise: Exercise): String? = when (exercise) {
        is Exercise.WriteFunction -> Screen.CodeExercise.createRoute(exercise.id, runId)
        is Exercise.MultiPartProblem -> Screen.Problem.createRoute(exercise.id, runId)
        else -> null
    }

    /**
     * Loads the target lesson from the curriculum by identifier.
     */
    fun loadLesson(
        lessonId: String,
        courseId: String = "kotlin-stripe",
        forceReload: Boolean = false
    ) {
        if (!forceReload && activeLesson?.id == lessonId && activeCourseRequestId == courseId) return

        val previousRunId = runId
        runId = newAttemptId()
        nextPresentationAttemptOrdinal = 1L
        val requestedRunId = runId
        viewModelScope.launch {
            progressRecorder.abandonLessonRun(previousRunId)
            if (requestedRunId != runId) return@launch
            _uiState.update {
                if (requestedRunId != runId) it else it.copy(isLoading = true, errorMessage = null)
            }
            val courses = courseRepository.listCourses()
            if (requestedRunId != runId) return@launch
            val targetCourse = courses.firstOrNull { it.id == courseId } ?: courses.firstOrNull()

            if (targetCourse == null) {
                _uiState.update {
                    if (requestedRunId != runId) it else it.copy(
                        isLoading = false,
                        errorMessage = "Course '$courseId' not found."
                    )
                }
                return@launch
            }

            var foundLesson: Lesson? = null
            var chapterTitle = ""

            for (chapter in targetCourse.chapters) {
                val lesson = chapter.lessons.firstOrNull { it.id == lessonId }
                if (lesson != null) {
                    foundLesson = lesson
                    chapterTitle = chapter.title
                    break
                }
            }

            if (foundLesson == null) {
                _uiState.update {
                    if (requestedRunId != runId) it else it.copy(
                        isLoading = false,
                        errorMessage = "Lesson '$lessonId' not found."
                    )
                }
                return@launch
            }

            if (!progressRecorder.beginLessonRun(foundLesson.id, requestedRunId)) {
                _uiState.update {
                    if (requestedRunId != runId) it else it.copy(
                        isLoading = false,
                        errorMessage = "Could not start this lesson run. Please try again."
                    )
                }
                return@launch
            }
            if (requestedRunId != runId) {
                progressRecorder.abandonLessonRun(requestedRunId)
                return@launch
            }

            activeLesson = foundLesson
            activeCourseRequestId = courseId
            exercises = foundLesson.exercises
            mistakeReviewQueue.clear()
            queuedMistakeIds.clear()
            masteredExerciseIds.clear()
            mistakeReviewTotal = 0
            mistakeReviewCorrectCount = 0

            _uiState.update {
                if (requestedRunId != runId) it else it.copy(
                    isLoading = false,
                    courseId = targetCourse.id,
                    lessonId = foundLesson.id,
                    lessonTitle = foundLesson.title,
                    chapterTitle = chapterTitle,
                    step = LessonStep.Explanation(foundLesson.explanation),
                    totalExercisesCount = exercises.size,
                    correctExercisesCount = 0,
                    xpEarnedInLesson = 0,
                    pendingMistakeCount = 0,
                    isMistakeReview = false,
                    selectedOptionIndex = null,
                    clozeAnswers = emptyMap(),
                    predictOutputText = "",
                    isAnswerChecked = false,
                    isAnswerCorrect = null,
                    currentExplanation = "",
                    aiPracticeState = AiPracticeState(),
                    consecutiveCorrectStreak = 0,
                    activeMilestone = null,
                    feedbackEvent = null
                )
            }
        }
    }

    /**
     * User clicks "Start Exercises" after reading the lesson explanation.
     */
    fun onStartPractice() {
        if (_uiState.value.step !is LessonStep.Explanation) return
        if (exercises.isEmpty()) {
            completeLesson()
            return
        }

        _uiState.update {
            it.copy(
                step = LessonStep.ExercisePlaying(
                    exerciseIndex = 0,
                    totalExercises = exercises.size,
                    exercise = exercises[0]
                ),
                selectedOptionIndex = null,
                clozeAnswers = emptyMap(),
                predictOutputText = "",
                isAnswerChecked = false,
                isAnswerCorrect = null,
                currentExplanation = ""
            )
        }
    }

    /** Starts the explicit end-of-lesson round for unique missed lesson cards. */
    fun startMistakeReview() {
        val state = _uiState.value
        if (state.step !is LessonStep.MistakeReviewIntro || mistakeReviewQueue.isEmpty()) return

        mistakeReviewTotal = mistakeReviewQueue.size
        mistakeReviewCorrectCount = 0
        _uiState.update {
            it.copy(
                step = LessonStep.ExercisePlaying(
                    exerciseIndex = 0,
                    totalExercises = mistakeReviewTotal,
                    exercise = mistakeReviewQueue.first()
                ),
                isMistakeReview = true,
                pendingMistakeCount = mistakeReviewQueue.size,
                selectedOptionIndex = null,
                clozeAnswers = emptyMap(),
                predictOutputText = "",
                isAnswerChecked = false,
                isAnswerCorrect = null,
                currentExplanation = ""
            )
        }
    }

    fun onOptionSelected(index: Int) {
        if (_uiState.value.isAnswerChecked) return
        _uiState.update { it.copy(selectedOptionIndex = index) }
    }

    fun onClozeAnswerChanged(placeholder: String, value: String) {
        if (_uiState.value.isAnswerChecked) return
        _uiState.update {
            val updatedMap = it.clozeAnswers.toMutableMap()
            updatedMap[placeholder] = value
            it.copy(clozeAnswers = updatedMap)
        }
    }

    fun onPredictOutputChanged(text: String) {
        if (_uiState.value.isAnswerChecked) return
        _uiState.update { it.copy(predictOutputText = text) }
    }

    /**
     * Evaluates the learner's answer against the active exercise.
     */
    fun onCheckAnswer() {
        val state = _uiState.value
        val currentStep = state.step as? LessonStep.ExercisePlaying ?: return
        if (state.isAnswerChecked) return

        val exercise = currentStep.exercise
        // Code and problem exercises are graded in their studio screens; the lesson records their result.
        if (routeForExercise(exercise) != null) return

        val (isCorrect, explanation) = when (exercise) {
            is Exercise.MultipleChoice -> {
                val selected = state.selectedOptionIndex ?: -1
                val correct = answerGrader.gradeMultipleChoice(exercise, selected)
                correct to exercise.explanation
            }
            is Exercise.Cloze -> {
                val correct = answerGrader.gradeCloze(exercise, state.clozeAnswers)
                correct to exercise.explanation
            }
            is Exercise.PredictOutput -> {
                val correct = answerGrader.gradePredictOutput(exercise, state.predictOutputText)
                correct to exercise.explanation
            }
            is Exercise.WriteFunction, is Exercise.MultiPartProblem -> return
        }

        if (!isCorrect && !state.isMistakeReview) enqueueMissedLessonCard(exercise)
        val newlyMastered = isCorrect && masteredExerciseIds.add(exercise.id)
        if (newlyMastered && state.isMistakeReview) mistakeReviewCorrectCount += 1
        val attemptId = generatePresentationAttemptId(exercise.id)
        val answerRunId = runId

        val newStreak = if (isCorrect) state.consecutiveCorrectStreak + 1 else 0
        val isMilestone = isCorrect && newStreak > 0 && (newStreak % 5 == 0)
        val eventId = generateEventId()
        val milestone = if (isMilestone) MilestoneCelebration(id = eventId, streakCount = newStreak) else null
        val feedbackEvent = LessonFeedbackEvent(id = eventId, correct = isCorrect, isMilestone = isMilestone, streak = newStreak)

        // Mark the answer checked before recording, so a second tap cannot record it again.
        _uiState.update {
            it.copy(
                isAnswerChecked = true,
                isAnswerCorrect = isCorrect,
                currentExplanation = explanation,
                correctExercisesCount = masteredExerciseIds.size.coerceAtMost(exercises.size),
                pendingMistakeCount = mistakeReviewQueue.size,
                consecutiveCorrectStreak = newStreak,
                activeMilestone = milestone ?: it.activeMilestone,
                feedbackEvent = feedbackEvent
            )
        }

        launchRecord {
            progressRecorder.record(
                ExerciseAttempt(
                    attemptId = attemptId,
                    exerciseId = exercise.id,
                    correct = isCorrect,
                    isLessonRetry = state.isMistakeReview,
                    lessonRunId = answerRunId
                )
            )
            refreshLessonXp(answerRunId)
        }
    }

    private fun enqueueMissedLessonCard(exercise: Exercise) {
        val isLessonCard = exercise is Exercise.MultipleChoice ||
            exercise is Exercise.Cloze ||
            exercise is Exercise.PredictOutput
        if (isLessonCard && queuedMistakeIds.add(exercise.id)) {
            mistakeReviewQueue += exercise
        }
    }

    /**
     * Called when a studio screen (code drill or multi-part problem) returns after a passing check.
     * The studio has already recorded the attempt, so this only advances the lesson, and only when
     * this run's staged results say the exercise passed.
     */
    fun onStudioExerciseCompleted(exerciseId: String, completedRunId: String? = runId) {
        val expectedRunId = completedRunId ?: return
        if (expectedRunId != runId) return
        val currentStep = _uiState.value.step as? LessonStep.ExercisePlaying ?: return
        if (currentStep.exercise.id != exerciseId || _uiState.value.isAnswerChecked) return

        viewModelScope.launch {
            val passed = progressRecorder.lessonRunHasPassed(expectedRunId, exerciseId)
            if (expectedRunId != runId) return@launch
            if (!passed) return@launch
            val xp = progressRecorder.lessonRunXp(expectedRunId)
            if (expectedRunId != runId) return@launch
            val latest = _uiState.value
            val latestPlaying = latest.step as? LessonStep.ExercisePlaying
            if (latestPlaying == null || latestPlaying.exercise.id != exerciseId || latest.isAnswerChecked) {
                return@launch
            }
            _uiState.update { state ->
                if (expectedRunId != runId) return@update state
                val playing = state.step as? LessonStep.ExercisePlaying
                if (playing == null || playing.exercise.id != exerciseId || state.isAnswerChecked) {
                    return@update state
                }
                masteredExerciseIds.add(exerciseId)
                val newStreak = state.consecutiveCorrectStreak + 1
                val isMilestone = newStreak > 0 && (newStreak % 5 == 0)
                val eventId = generateEventId()
                val milestone = if (isMilestone) MilestoneCelebration(id = eventId, streakCount = newStreak) else null
                val feedbackEvent = LessonFeedbackEvent(id = eventId, correct = true, isMilestone = isMilestone, streak = newStreak)

                state.copy(
                    isAnswerChecked = true,
                    isAnswerCorrect = true,
                    currentExplanation = when (val exercise = playing.exercise) {
                        is Exercise.WriteFunction -> exercise.explanation ?: "Completed code function."
                        is Exercise.MultiPartProblem -> "Completed problem."
                        else -> "Exercise completed."
                    },
                    xpEarnedInLesson = xp,
                    correctExercisesCount = masteredExerciseIds.size.coerceAtMost(exercises.size),
                    pendingMistakeCount = mistakeReviewQueue.size,
                    consecutiveCorrectStreak = newStreak,
                    activeMilestone = milestone ?: state.activeMilestone,
                    feedbackEvent = feedbackEvent
                )
            }
        }
    }

    /** XP earned so far in this run; it remains provisional until settlement. */
    private suspend fun refreshLessonXp(expectedRunId: String) {
        val xp = progressRecorder.lessonRunXp(expectedRunId)
        if (expectedRunId != runId) return
        _uiState.update { state ->
            if (expectedRunId != runId) return@update state
            val step = state.step
            state.copy(
                xpEarnedInLesson = xp,
                step = if (step is LessonStep.Completed) step.copy(totalXp = xp) else step
            )
        }
    }

    /**
     * Advances to the next exercise or transitions to the summary completion view.
     */
    fun onContinueNext() {
        val state = _uiState.value
        val currentStep = state.step as? LessonStep.ExercisePlaying ?: return
        if (!state.isAnswerChecked) return

        if (state.isMistakeReview) {
            val currentMistake = mistakeReviewQueue.firstOrNull() ?: run {
                completeLesson()
                return
            }
            if (currentMistake.id != currentStep.exercise.id) return

            mistakeReviewQueue.removeAt(0)
            if (state.isAnswerCorrect != true) {
                // Keep one pending copy and offer it after every other unresolved mistake.
                mistakeReviewQueue += currentMistake
            }

            if (mistakeReviewQueue.isEmpty()) {
                completeLesson()
            } else {
                _uiState.update {
                    it.copy(
                        step = LessonStep.ExercisePlaying(
                            exerciseIndex = mistakeReviewCorrectCount,
                            totalExercises = mistakeReviewTotal,
                            exercise = mistakeReviewQueue.first()
                        ),
                        pendingMistakeCount = mistakeReviewQueue.size,
                        selectedOptionIndex = null,
                        clozeAnswers = emptyMap(),
                        predictOutputText = "",
                        isAnswerChecked = false,
                        isAnswerCorrect = null,
                        currentExplanation = "",
                        activeMilestone = null,
                        feedbackEvent = null
                    )
                }
            }
            return
        }

        val nextIndex = currentStep.exerciseIndex + 1

        if (nextIndex < exercises.size) {
            _uiState.update {
                it.copy(
                    step = LessonStep.ExercisePlaying(
                        exerciseIndex = nextIndex,
                        totalExercises = exercises.size,
                        exercise = exercises[nextIndex]
                    ),
                    selectedOptionIndex = null,
                    clozeAnswers = emptyMap(),
                    predictOutputText = "",
                    isAnswerChecked = false,
                    isAnswerCorrect = null,
                    currentExplanation = "",
                    activeMilestone = null,
                    feedbackEvent = null
                )
            }
        } else if (mistakeReviewQueue.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    step = LessonStep.MistakeReviewIntro(pendingCount = mistakeReviewQueue.size),
                    pendingMistakeCount = mistakeReviewQueue.size,
                    isMistakeReview = false,
                    selectedOptionIndex = null,
                    clozeAnswers = emptyMap(),
                    predictOutputText = "",
                    isAnswerChecked = false,
                    isAnswerCorrect = null,
                    currentExplanation = "",
                    activeMilestone = null,
                    feedbackEvent = null
                )
            }
        } else {
            completeLesson()
        }
    }

    private fun completeLesson() {
        if (_uiState.value.step is LessonStep.Completed || _uiState.value.step is LessonStep.Completing) return
        val lessonId = activeLesson?.id
        if (lessonId == null || !masteredExerciseIds.containsAll(exercises.map { it.id })) return
        val settlementRunId = runId
        _uiState.update {
            if (settlementRunId != runId) it else it.copy(
                step = LessonStep.Completing,
                pendingMistakeCount = 0,
                isMistakeReview = false,
                isAnswerChecked = false,
                activeMilestone = null,
                feedbackEvent = null
            )
        }
        startSettlement(lessonId, settlementRunId)
    }

    private fun launchRecord(block: suspend () -> Unit) {
        if (pendingRecordCount == 0) pendingRecordsComplete = CompletableDeferred()
        pendingRecordCount += 1
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // A checked answer is already accepted by the lesson flow. Finish staging it even
                // if the destination is popped; an explicit abandon then discards the whole run.
                withContext(NonCancellable) { block() }
            } finally {
                pendingRecordCount -= 1
                if (pendingRecordCount == 0) pendingRecordsComplete.complete(Unit)
            }
        }
    }

    private fun startSettlement(lessonId: String, settlementRunId: String) {
        if (settlementJob?.isActive == true && settlementJobRunId == settlementRunId) return
        settlementJobRunId = settlementRunId
        settlementJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(NonCancellable) {
                    pendingRecordsComplete.await()
                    when (progressRecorder.settleLessonRun(lessonId, settlementRunId)) {
                        is LessonRunSettlementOutcome.Settled,
                        LessonRunSettlementOutcome.Duplicate -> showCompletedSummary(settlementRunId)
                        LessonRunSettlementOutcome.Rejected -> showSettlementFailure(
                            settlementRunId,
                            "This lesson run is missing a completed answer. Retry saving or return to the lesson."
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showSettlementFailure(
                    settlementRunId,
                    "We couldn't save this lesson yet. Your progress is still here; try again."
                )
            }
        }
    }

    private suspend fun showCompletedSummary(expectedRunId: String) {
        val xp = progressRecorder.lessonRunXp(expectedRunId)
        if (expectedRunId != runId) return
        _uiState.update { state ->
            if (expectedRunId != runId) return@update state
            state.copy(
                xpEarnedInLesson = xp,
                step = LessonStep.Completed(
                    totalXp = xp,
                    correctCount = masteredExerciseIds.size.coerceAtMost(exercises.size),
                    totalCount = exercises.size
                ),
                correctExercisesCount = masteredExerciseIds.size.coerceAtMost(exercises.size)
            )
        }
    }

    private fun showSettlementFailure(expectedRunId: String, message: String) {
        _uiState.update { state ->
            if (expectedRunId != runId) state else state.copy(step = LessonStep.SettlementFailed(message))
        }
    }

    fun retrySettlement() {
        val lessonId = activeLesson?.id ?: return
        if (_uiState.value.step !is LessonStep.SettlementFailed) return
        val settlementRunId = runId
        _uiState.update { it.copy(step = LessonStep.Completing) }
        startSettlement(lessonId, settlementRunId)
    }

    /** Drops any unfinished staging before leaving the lesson destination. */
    fun abandonLessonRun(onAbandoned: () -> Unit) {
        val leavingRunId = runId
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            progressRecorder.abandonLessonRun(leavingRunId)
            onAbandoned()
        }
    }

    override fun onCleared() {
        val leavingRunId = runId
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        cleanupScope.launch {
            try {
                progressRecorder.abandonLessonRun(leavingRunId)
            } finally {
                cleanupScope.cancel()
            }
        }
        super.onCleared()
    }

    /**
     * Acknowledges that a feedback event (sound/haptics) has been consumed, preventing replays.
     */
    fun acknowledgeFeedbackEvent(id: Long) {
        _uiState.update { state ->
            if (state.feedbackEvent?.id == id) state.copy(feedbackEvent = null) else state
        }
    }

    /**
     * Dismisses an active milestone celebration.
     */
    fun dismissMilestone(id: Long? = null) {
        _uiState.update { state ->
            if (id == null || state.activeMilestone?.id == id) state.copy(activeMilestone = null) else state
        }
    }

    /**
     * Consumes a milestone after the screen has captured it for local presentation.
     * Clearing the state-backed event prevents screen recreation from replaying its animation.
     */
    fun acknowledgeMilestonePresentation(id: Long) {
        dismissMilestone(id)
    }

    /**
     * Triggers AI generation of 3 practice cards on the active lesson's topic.
     * Cards are shown in a practice sheet, labeled "AI-generated, unverified",
     * award no XP, never enter review, and are never saved to course content.
     */
    fun requestMoreLikeThis() {
        if (isInterviewMode) return
        if (!hasApiKey) {
            _uiState.update {
                it.copy(
                    aiPracticeState = it.aiPracticeState.copy(
                        isVisible = true,
                        errorMessage = "Add your API key in Settings"
                    )
                )
            }
            return
        }
        val tutor = aiTutor ?: return
        val currentTopic = activeLesson?.title ?: _uiState.value.lessonTitle.ifBlank { "Kotlin" }

        _uiState.update {
            it.copy(
                aiPracticeState = it.aiPracticeState.copy(
                    isVisible = true,
                    isLoading = true,
                    errorMessage = null,
                    cards = emptyList(),
                    currentCardIndex = 0
                )
            )
        }

        viewModelScope.launch {
            when (val result = tutor.generateCards(topic = currentTopic, count = 3)) {
                is AiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            aiPracticeState = it.aiPracticeState.copy(
                                isLoading = false,
                                cards = result.data,
                                currentCardIndex = 0,
                                selectedOptionIndex = null,
                                clozeAnswers = emptyMap(),
                                isAnswerChecked = false,
                                isAnswerCorrect = null,
                                explanation = ""
                            )
                        )
                    }
                }
                is AiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            aiPracticeState = it.aiPracticeState.copy(
                                isLoading = false,
                                errorMessage = result.message
                            )
                        )
                    }
                }
            }
        }
    }

    fun onAiPracticeOptionSelected(index: Int) {
        if (_uiState.value.aiPracticeState.isAnswerChecked) return
        _uiState.update {
            it.copy(aiPracticeState = it.aiPracticeState.copy(selectedOptionIndex = index))
        }
    }

    fun onAiPracticeClozeAnswerChanged(placeholder: String, value: String) {
        if (_uiState.value.aiPracticeState.isAnswerChecked) return
        _uiState.update {
            val updated = it.aiPracticeState.clozeAnswers.toMutableMap()
            updated[placeholder] = value
            it.copy(aiPracticeState = it.aiPracticeState.copy(clozeAnswers = updated))
        }
    }

    fun checkAiPracticeAnswer() {
        val practice = _uiState.value.aiPracticeState
        val card = practice.currentCard ?: return
        if (practice.isAnswerChecked) return

        val (isCorrect, explanation) = when (card) {
            is Exercise.MultipleChoice -> {
                val selected = practice.selectedOptionIndex ?: -1
                val correct = answerGrader.gradeMultipleChoice(card, selected)
                correct to card.explanation
            }
            is Exercise.Cloze -> {
                val correct = answerGrader.gradeCloze(card, practice.clozeAnswers)
                correct to card.explanation
            }
            is Exercise.PredictOutput -> false to card.explanation
            is Exercise.WriteFunction, is Exercise.MultiPartProblem -> return
        }

        // AI practice cards award no XP, never enter review, and are not saved in course progress.
        _uiState.update {
            it.copy(
                aiPracticeState = it.aiPracticeState.copy(
                    isAnswerChecked = true,
                    isAnswerCorrect = isCorrect,
                    explanation = explanation
                )
            )
        }
    }

    fun nextAiPracticeCard() {
        val practice = _uiState.value.aiPracticeState
        val nextIndex = practice.currentCardIndex + 1
        if (nextIndex < practice.cards.size) {
            _uiState.update {
                it.copy(
                    aiPracticeState = it.aiPracticeState.copy(
                        currentCardIndex = nextIndex,
                        selectedOptionIndex = null,
                        clozeAnswers = emptyMap(),
                        isAnswerChecked = false,
                        isAnswerCorrect = null,
                        explanation = ""
                    )
                )
            }
        } else {
            dismissAiPractice()
        }
    }

    fun dismissAiPractice() {
        _uiState.update {
            it.copy(aiPracticeState = it.aiPracticeState.copy(isVisible = false))
        }
    }

    companion object {
        fun provideFactory(
            appContainer: AppContainer,
            isInterviewMode: Boolean = false,
            hasApiKey: Boolean = false
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LessonViewModel(
                        appContainer = appContainer,
                        isInterviewMode = isInterviewMode,
                        hasApiKey = hasApiKey
                    ) as T
                }
            }
    }
}
