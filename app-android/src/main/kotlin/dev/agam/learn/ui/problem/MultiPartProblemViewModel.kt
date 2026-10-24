package dev.agam.learn.ui.problem

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile
import dev.agam.learn.ui.code.AiDialogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel managing multi-part problem progression, timer, per-part code storage,
 * and compiler execution.
 */
class MultiPartProblemViewModel(
    private val problem: Exercise.MultiPartProblem,
    private val codeRunner: CodeRunner,
    private val aiTutor: AiTutor? = null,
    isInterviewModeDefault: Boolean = false,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    // Code per part index
    private val partCodes = mutableMapOf<Int, TextFieldValue>()
    // Passed status per part index
    private val partPassed = mutableMapOf<Int, Boolean>()

    private var timerJob: Job? = null

    private val _uiState: MutableStateFlow<MultiPartProblemUiState>

    init {
        val initialPart = problem.parts.firstOrNull()
        val initialCode = TextFieldValue(initialPart?.starterCode ?: "")
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
                timerSeconds = 45 * 60,
                isTimerRunning = false
            )
        )
    }

    val uiState: StateFlow<MultiPartProblemUiState> = _uiState.asStateFlow()

    fun onCodeChange(newCode: TextFieldValue) {
        val currentIndex = _uiState.value.currentPartIndex
        partCodes[currentIndex] = newCode
        _uiState.update { it.copy(currentCode = newCode) }
    }

    fun selectPart(index: Int) {
        if (index >= _uiState.value.unlockedPartCount || index >= problem.parts.size) return
        val codeForPart = partCodes[index] ?: TextFieldValue(problem.parts[index].starterCode ?: "")
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
        val nextIndex = currentState.currentPartIndex + 1
        if (nextIndex >= problem.parts.size) return
        if (!currentState.isCurrentPartPassed) return

        val newUnlockedCount = maxOf(currentState.unlockedPartCount, nextIndex + 1)
        val nextPart = problem.parts[nextIndex]

        // Next part code defaults to working code from previous part if next part has no custom starter code
        val initialNextCode = if (!partCodes.containsKey(nextIndex)) {
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

    fun checkPart() {
        val currentIndex = _uiState.value.currentPartIndex
        val currentPart = problem.parts.getOrNull(currentIndex) ?: return
        val currentCode = _uiState.value.currentCode.text

        _uiState.update { it.copy(isChecking = true, currentOutcome = null) }

        scope.launch {
            val outcome = try {
                codeRunner.test(
                    files = listOf(SourceFile(name = "Solution.kt", text = currentCode)),
                    tests = listOf(SourceFile(name = "Part${currentIndex + 1}Test.kt", text = currentPart.hiddenTests))
                )
            } catch (e: Exception) {
                RunOutcome.Unavailable(reason = e.message ?: "Test grading failed")
            }

            val passed = outcome is RunOutcome.Passed
            if (passed) {
                partPassed[currentIndex] = true
            }

            _uiState.update {
                it.copy(
                    isChecking = false,
                    currentOutcome = outcome,
                    isCurrentPartPassed = passed || (partPassed[currentIndex] == true)
                )
            }
        }
    }

    fun toggleInterviewMode(enabled: Boolean) {
        _uiState.update { it.copy(isInterviewMode = enabled) }
    }

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
        if (_uiState.value.isInterviewMode) return

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
        if (_uiState.value.isInterviewMode) return

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
        if (_uiState.value.isInterviewMode) return

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
