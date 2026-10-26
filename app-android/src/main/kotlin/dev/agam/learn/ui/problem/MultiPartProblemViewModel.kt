package dev.agam.learn.ui.problem

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.ai.DynamicAiTutor
import dev.agam.learn.data.ExerciseAttempt
import dev.agam.learn.data.ProblemPartAttempt
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.RecordOutcome
import dev.agam.learn.data.newAttemptId
import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.domain.CodePuzzleBlankFeedback
import dev.agam.learn.domain.DefaultAnswerGrader
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.FaqItem
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile
import dev.agam.learn.ui.code.AiDialogState
import dev.agam.learn.ui.lesson.components.renderPuzzleCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * State representation for the Multi-part Problem screen.
 */
data class MultiPartProblemUiState(
    val problemId: String = "",
    val prompt: String = "",
    val story: String = "",
    val parts: List<ProblemPart> = emptyList(),
    val faq: List<FaqItem> = emptyList(),
    val currentPartIndex: Int = 0,
    val unlockedPartCount: Int = 1,
    val currentCode: TextFieldValue = TextFieldValue(""),
    val isRunning: Boolean = false,
    val isChecking: Boolean = false,
    val currentOutcome: RunOutcome? = null,
    val isCurrentPartPassed: Boolean = false,
    /** True only when every part passes. */
    val allPartsPassed: Boolean = false,
    val isInterviewMode: Boolean = false,
    val isAiAvailable: Boolean = false,
    val hasApiKey: Boolean = false,
    val timerSeconds: Int = 45 * 60,
    val isTimerRunning: Boolean = false,
    val aiDialogState: AiDialogState? = null,
    val partPuzzleAnswers: Map<Int, Map<String, String>> = emptyMap(),
    val partChecked: Map<Int, Boolean> = emptyMap(),
    val partPassed: Map<Int, Boolean> = emptyMap(),
    val partFeedback: Map<Int, List<CodePuzzleBlankFeedback>> = emptyMap(),
    val recordError: String? = null,
    val onSelectOption: (placeholder: String, option: String) -> Unit = { _, _ -> },
    val onRetry: () -> Unit = {}
)

/**
 * ViewModel managing multi-part problem progression, timer, offline multi-blank code puzzles,
 * and backwards-compatible compiler execution.
 */
class MultiPartProblemViewModel(
    private val problem: Exercise.MultiPartProblem,
    private val codeRunner: CodeRunner,
    private val progressRecorder: ProgressRecorder,
    aiTutor: AiTutor? = null,
    isInterviewModeDefault: Boolean = false,
    /** False when no API key is saved; the AI controls then show the add-key hint and do nothing. */
    hasApiKey: Boolean = false,
    /** Present when this problem was opened from a lesson; its attempts then wait for lesson settlement. */
    private val lessonRunId: String? = null,
    coroutineScope: CoroutineScope? = null,
    private val answerGrader: AnswerGrader = DefaultAnswerGrader()
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope
    private val pendingPartAttemptIds = mutableMapOf<Int, String>()
    private var parentCompletionRecorded = false

    /** Identifies this visit to the problem; the completion is recorded once per visit. */
    private val runId: String = newAttemptId()

    private val aiTutor: AiTutor? = if (aiTutor is DynamicAiTutor) {
        // A running mock refuses AI just like interview mode does.
        aiTutor.forSession { _uiState.value.isInterviewMode || _uiState.value.isTimerRunning }
    } else {
        aiTutor
    }

    // Code per part index
    private val partCodes = mutableMapOf<Int, TextFieldValue>()
    // Passed status per part index
    private val partPassed = mutableMapOf<Int, Boolean>()

    private var timerJob: Job? = null

    private val _uiState: MutableStateFlow<MultiPartProblemUiState>

    init {
        val initialPart = problem.parts.firstOrNull()
        val initialGuided = initialPart?.guided
        val initialCode = if (initialGuided != null) {
            TextFieldValue(
                renderPuzzleCode(
                    initialGuided.template,
                    initialGuided.blanks.map { it.placeholder },
                    emptyMap()
                )
            )
        } else {
            TextFieldValue(initialPart?.starterCode ?: "")
        }
        partCodes[0] = initialCode

        _uiState = MutableStateFlow(
            MultiPartProblemUiState(
                problemId = problem.id,
                prompt = problem.prompt,
                story = problem.story,
                parts = problem.parts,
                faq = problem.faq,
                currentPartIndex = 0,
                unlockedPartCount = 1,
                currentCode = initialCode,
                isInterviewMode = isInterviewModeDefault,
                isAiAvailable = aiTutor != null,
                hasApiKey = hasApiKey,
                timerSeconds = 45 * 60,
                isTimerRunning = false,
                onSelectOption = { placeholder, option -> selectOption(placeholder, option) },
                onRetry = { retryCurrentPart() }
            )
        )
    }

    val uiState: StateFlow<MultiPartProblemUiState> = _uiState.asStateFlow()
    private val _feedbackEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    /** One-shot feedback for graded part checks, with no replay when the screen is revisited. */
    val feedbackEvents: SharedFlow<Boolean> = _feedbackEvents.asSharedFlow()

    fun selectOption(placeholder: String, option: String) {
        val state = _uiState.value
        val currentIndex = state.currentPartIndex
        if (state.isChecking || state.recordError != null || state.partChecked[currentIndex] == true) return
        val currentPart = problem.parts.getOrNull(currentIndex) ?: return
        val puzzle = currentPart.guided ?: return
        val blank = puzzle.blanks.firstOrNull { it.placeholder == placeholder } ?: return
        val offeredOption = blank.options.firstOrNull { it == option } ?: return
        _uiState.update { current ->
            if (current.isChecking || current.recordError != null || current.partChecked[currentIndex] == true) return@update current
            val currentAnswers = current.partPuzzleAnswers[currentIndex].orEmpty().toMutableMap()
            currentAnswers[placeholder] = offeredOption
            val updatedMap = current.partPuzzleAnswers + (currentIndex to currentAnswers)

            val currentPart = problem.parts.getOrNull(currentIndex)
            val guided = currentPart?.guided
            val rendered = if (guided != null) {
                renderPuzzleCode(
                    guided.template,
                    guided.blanks.map { it.placeholder },
                    currentAnswers
                )
            } else {
                current.currentCode.text
            }
            partCodes[currentIndex] = TextFieldValue(rendered)
            current.copy(
                partPuzzleAnswers = updatedMap,
                currentCode = TextFieldValue(rendered),
                recordError = null
            )
        }
    }

    fun retryCurrentPart() {
        if (_uiState.value.isChecking || _uiState.value.recordError != null) return
        val currentIndex = _uiState.value.currentPartIndex
        partPassed.remove(currentIndex)
        _uiState.update { current ->
            val updatedChecked = current.partChecked + (currentIndex to false)
            val updatedPassed = current.partPassed + (currentIndex to false)
            val updatedFeedback = current.partFeedback + (currentIndex to emptyList())
            current.copy(
                partChecked = updatedChecked,
                partPassed = updatedPassed,
                partFeedback = updatedFeedback,
                isCurrentPartPassed = false,
                allPartsPassed = false,
                recordError = null
            )
        }
    }

    fun onCodeChange(newCode: TextFieldValue) {
        val currentIndex = _uiState.value.currentPartIndex
        val changed = partCodes[currentIndex]?.text != newCode.text
        partCodes[currentIndex] = newCode
        if (changed) partPassed.remove(currentIndex)
        _uiState.update {
            it.copy(
                currentCode = newCode,
                isCurrentPartPassed = partPassed[currentIndex] == true,
                allPartsPassed = allPartsPassed(),
                currentOutcome = if (changed && it.currentOutcome is RunOutcome.Passed) null else it.currentOutcome
            )
        }
    }

    private fun allPartsPassed(): Boolean = problem.parts.indices.all { partPassed[it] == true }

    fun selectPart(index: Int) {
        if (_uiState.value.isChecking || index >= _uiState.value.unlockedPartCount || index >= problem.parts.size) return
        val currentPart = problem.parts[index]
        val guided = currentPart.guided
        val currentAnswers = _uiState.value.partPuzzleAnswers[index].orEmpty()
        val codeForPart = if (guided != null) {
            TextFieldValue(
                renderPuzzleCode(
                    guided.template,
                    guided.blanks.map { it.placeholder },
                    currentAnswers
                )
            )
        } else {
            partCodes[index] ?: TextFieldValue(currentPart.starterCode ?: "")
        }
        _uiState.update {
            it.copy(
                currentPartIndex = index,
                currentCode = codeForPart,
                currentOutcome = null,
                isCurrentPartPassed = partPassed[index] == true
            )
        }
    }

    fun revealNextPart() {
        val currentState = _uiState.value
        if (currentState.isChecking) return
        val nextIndex = currentState.currentPartIndex + 1
        if (nextIndex >= problem.parts.size) return
        if (!currentState.isCurrentPartPassed) return

        val newUnlockedCount = maxOf(currentState.unlockedPartCount, nextIndex + 1)
        val nextPart = problem.parts[nextIndex]

        val nextGuided = nextPart.guided
        val initialNextCode = if (nextGuided != null) {
            val answers = currentState.partPuzzleAnswers[nextIndex].orEmpty()
            TextFieldValue(
                renderPuzzleCode(
                    nextGuided.template,
                    nextGuided.blanks.map { it.placeholder },
                    answers
                )
            )
        } else if (!partCodes.containsKey(nextIndex)) {
            val codeText = nextPart.starterCode ?: currentState.currentCode.text
            TextFieldValue(codeText)
        } else {
            partCodes[nextIndex]!!
        }
        partCodes[nextIndex] = initialNextCode

        _uiState.update {
            it.copy(
                currentPartIndex = nextIndex,
                unlockedPartCount = newUnlockedCount,
                currentCode = initialNextCode,
                currentOutcome = null,
                isCurrentPartPassed = partPassed[nextIndex] == true
            )
        }
    }

    /**
     * Compiler run action retained for developer/backwards testing. Unreachable in normal UI.
     */
    fun runCode() {
        val currentCode = _uiState.value.currentCode.text
        _uiState.update { it.copy(isRunning = true, currentOutcome = null) }

        scope.launch {
            val outcome = try {
                codeRunner.run(
                    files = listOf(SourceFile(name = "Solution.kt", text = currentCode))
                )
            } catch (e: Exception) {
                RunOutcome.Unavailable(reason = e.message ?: "Run execution failed")
            }

            _uiState.update {
                it.copy(isRunning = false, currentOutcome = outcome)
            }
        }
    }

    /**
     * Checks the current part. For guided puzzles, performs offline deterministic local grading.
     * When puzzle data is absent, falls back to codeRunner for backwards-compatible test calls.
     */
    fun checkPart() {
        val state = _uiState.value
        if (state.isChecking) return
        val currentIndex = state.currentPartIndex
        val currentPart = problem.parts.getOrNull(currentIndex) ?: return
        val guided = currentPart.guided

        if (guided != null) {
            if (state.partChecked[currentIndex] == true) return
            val currentAnswers = state.partPuzzleAnswers[currentIndex].orEmpty()
            val allAnswered = guided.blanks.isNotEmpty() && guided.blanks.all {
                val answer = currentAnswers[it.placeholder]
                !answer.isNullOrBlank() && it.options.isNotEmpty() && answer in it.options
            }
            if (!allAnswered) return

            val partAttemptId = pendingPartAttemptIds.getOrPut(currentIndex) { newAttemptId() }
            _uiState.update { it.copy(isChecking = true, recordError = null) }

            val gradeResult = answerGrader.gradeCodePuzzleWithFeedback(guided, currentAnswers)
            val passed = gradeResult.isCorrect

            scope.launch {
                val partOutcome = try {
                    progressRecorder.record(
                        ProblemPartAttempt(
                            attemptId = partAttemptId,
                            problemId = problem.id,
                            partId = currentPart.id,
                            correct = passed,
                            lessonRunId = lessonRunId
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }

                if (partOutcome == null || partOutcome == RecordOutcome.Rejected) {
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            recordError = "Could not save progress. Check again to retry."
                        )
                    }
                    return@launch
                }

                if (passed) {
                    partPassed[currentIndex] = true
                    if (allPartsPassed() && !parentCompletionRecorded) {
                        val parentOutcome = try {
                            progressRecorder.record(
                            ExerciseAttempt(
                                attemptId = "$runId:complete",
                                exerciseId = problem.id,
                                correct = true,
                                lessonRunId = lessonRunId
                            )
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        if (parentOutcome == null || parentOutcome == RecordOutcome.Rejected) {
                            _uiState.update {
                                it.copy(
                                    isChecking = false,
                                    recordError = "Could not save progress. Check again to retry."
                                )
                            }
                            return@launch
                        }
                        parentCompletionRecorded = true
                    }
                }

                pendingPartAttemptIds.remove(currentIndex)
                _feedbackEvents.tryEmit(passed)
                _uiState.update { current ->
                    val updatedPassed = current.partPassed + (currentIndex to passed)
                    val updatedChecked = current.partChecked + (currentIndex to true)
                    val updatedFeedback = current.partFeedback + (currentIndex to gradeResult.feedback)
                    val isAllPassed = parentCompletionRecorded && allPartsPassed()
                    current.copy(
                        isChecking = false,
                        partChecked = updatedChecked,
                        partPassed = updatedPassed,
                        partFeedback = updatedFeedback,
                        isCurrentPartPassed = passed,
                        allPartsPassed = isAllPassed,
                        recordError = null
                    )
                }
            }
            return
        }

        // Backwards compatibility branch for legacy exercises without guided puzzle data
        val checkedSource = state.currentCode.text
        _uiState.update { it.copy(isChecking = true, currentOutcome = null) }

        scope.launch {
            val outcome = try {
                codeRunner.test(
                    files = listOf(SourceFile(name = "Solution.kt", text = checkedSource)),
                    tests = listOf(SourceFile(name = "Part${currentIndex + 1}Test.kt", text = currentPart.hiddenTests))
                )
            } catch (e: Exception) {
                RunOutcome.Unavailable(reason = e.message ?: "Test grading failed")
            }

            val passed = outcome is RunOutcome.Passed
            when (outcome) {
                is RunOutcome.Passed -> _feedbackEvents.tryEmit(true)
                is RunOutcome.TestsFailed, is RunOutcome.CompileError -> _feedbackEvents.tryEmit(false)
                else -> Unit
            }
            if (passed || outcome is RunOutcome.TestsFailed || outcome is RunOutcome.CompileError) {
                progressRecorder.record(
                    ProblemPartAttempt(
                        attemptId = newAttemptId(),
                        problemId = problem.id,
                        partId = currentPart.id,
                        correct = passed,
                        lessonRunId = lessonRunId
                    )
                )
            }
            if (passed && partCodes[currentIndex]?.text == checkedSource) {
                partPassed[currentIndex] = true
            }
            if (allPartsPassed()) {
                progressRecorder.record(
                    ExerciseAttempt(
                        attemptId = "$runId:complete",
                        exerciseId = problem.id,
                        correct = true,
                        lessonRunId = lessonRunId
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isChecking = false,
                    currentOutcome = outcome,
                    isCurrentPartPassed = if (it.currentPartIndex == currentIndex) {
                        partPassed[currentIndex] == true
                    } else {
                        it.isCurrentPartPassed
                    },
                    allPartsPassed = allPartsPassed()
                )
            }
        }
    }

    /**
     * The session toggle is free only when no timer is running: a mock cannot be switched
     * off or on part way through.
     */
    fun toggleInterviewMode(enabled: Boolean) {
        if (_uiState.value.isTimerRunning) return
        _uiState.update { it.copy(isInterviewMode = enabled) }
    }

    /** AI is refused in interview mode, while the mock timer runs, and without a saved key. */
    private fun aiAllowed(state: MultiPartProblemUiState): Boolean =
        !state.isInterviewMode && !state.isTimerRunning && state.hasApiKey

    fun toggleTimer() {
        val currentlyRunning = _uiState.value.isTimerRunning
        if (currentlyRunning) {
            timerJob?.cancel()
            timerJob = null
            _uiState.update { it.copy(isTimerRunning = false) }
        } else {
            _uiState.update { it.copy(isTimerRunning = true) }
            timerJob = scope.launch {
                while (isActive && _uiState.value.timerSeconds > 0) {
                    delay(1000)
                    _uiState.update {
                        val newSeconds = it.timerSeconds - 1
                        if (newSeconds <= 0) {
                            it.copy(timerSeconds = 0, isTimerRunning = false)
                        } else {
                            it.copy(timerSeconds = newSeconds)
                        }
                    }
                }
            }
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(timerSeconds = 45 * 60, isTimerRunning = false) }
    }

    fun askAiExplainMistake() {
        val tutor = aiTutor ?: return
        if (!aiAllowed(_uiState.value)) return

        val currentPart = problem.parts.getOrNull(_uiState.value.currentPartIndex) ?: return
        val dummyExercise = Exercise.WriteFunction(
            id = currentPart.id,
            prompt = currentPart.prompt,
            starterCode = currentPart.starterCode ?: "",
            hiddenTests = currentPart.hiddenTests,
            referenceSolution = currentPart.referenceSolution
        )

        _uiState.update {
            it.copy(
                aiDialogState = AiDialogState(
                    title = "Explain My Mistake (Part ${_uiState.value.currentPartIndex + 1})",
                    isLoading = true
                )
            )
        }

        scope.launch {
            val result = tutor.explainMistake(
                exercise = dummyExercise,
                userAnswer = _uiState.value.currentCode.text,
                expectedResult = currentPart.referenceSolution
            )
            handleAiResult(result, "Explain My Mistake")
        }
    }

    fun askAiHint() {
        val tutor = aiTutor ?: return
        if (!aiAllowed(_uiState.value)) return

        val currentPart = problem.parts.getOrNull(_uiState.value.currentPartIndex) ?: return
        val dummyExercise = Exercise.WriteFunction(
            id = currentPart.id,
            prompt = currentPart.prompt,
            starterCode = currentPart.starterCode ?: "",
            hiddenTests = currentPart.hiddenTests,
            referenceSolution = currentPart.referenceSolution
        )

        _uiState.update {
            it.copy(
                aiDialogState = AiDialogState(
                    title = "AI Hint (Part ${_uiState.value.currentPartIndex + 1})",
                    isLoading = true
                )
            )
        }

        scope.launch {
            val result = tutor.hint(exercise = dummyExercise, level = 1)
            handleAiResult(result, "AI Hint")
        }
    }

    fun askAiReview() {
        val tutor = aiTutor ?: return
        if (!aiAllowed(_uiState.value)) return

        val currentPart = problem.parts.getOrNull(_uiState.value.currentPartIndex) ?: return
        val dummyExercise = Exercise.WriteFunction(
            id = currentPart.id,
            prompt = currentPart.prompt,
            starterCode = currentPart.starterCode ?: "",
            hiddenTests = currentPart.hiddenTests,
            referenceSolution = currentPart.referenceSolution
        )

        _uiState.update {
            it.copy(
                aiDialogState = AiDialogState(
                    title = "AI Code Review (Part ${_uiState.value.currentPartIndex + 1})",
                    isLoading = true
                )
            )
        }

        scope.launch {
            val result = tutor.reviewCode(exercise = dummyExercise, code = _uiState.value.currentCode.text)
            handleAiResult(result, "AI Code Review")
        }
    }

    private fun handleAiResult(result: AiResult<String>, title: String) {
        _uiState.update { current ->
            when (result) {
                is AiResult.Success -> current.copy(
                    aiDialogState = AiDialogState(
                        title = title,
                        content = result.data,
                        isLoading = false,
                        isError = false
                    )
                )
                is AiResult.Error -> current.copy(
                    aiDialogState = AiDialogState(
                        title = title,
                        content = result.message + (result.cause?.let { "\nCause: $it" } ?: ""),
                        isLoading = false,
                        isError = true
                    )
                )
            }
        }
    }

    fun dismissAiDialog() {
        _uiState.update { it.copy(aiDialogState = null) }
    }

    fun dismissOutcome() {
        _uiState.update { it.copy(currentOutcome = null) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
