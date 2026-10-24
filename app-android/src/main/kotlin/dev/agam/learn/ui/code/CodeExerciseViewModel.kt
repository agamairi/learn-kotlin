package dev.agam.learn.ui.code

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.data.ExerciseAttempt
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.newAttemptId
import dev.agam.learn.model.Exercise
import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing code editing, compiler test executions, progressive hints,
 * and AI tutor interactions for a coding exercise.
 */
class CodeExerciseViewModel(
    private val exercise: Exercise.WriteFunction,
    private val codeRunner: CodeRunner,
    private val progressRecorder: ProgressRecorder,
    private val aiTutor: AiTutor? = null,
    val isInterviewMode: Boolean = false,
    /** False when no API key is saved; the AI controls then show the add-key hint and do nothing. */
    val hasApiKey: Boolean = false,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        CodeExerciseUiState(
            exerciseId = exercise.id,
            prompt = exercise.prompt,
            code = TextFieldValue(exercise.starterCode),
            starterCode = exercise.starterCode,
            hints = exercise.hints,
            revealedHintsCount = 0,
            isAiAvailable = aiTutor != null,
            isInterviewMode = isInterviewMode,
            hasApiKey = hasApiKey
        )
    )
    val uiState: StateFlow<CodeExerciseUiState> = _uiState.asStateFlow()

    private val aiHintsUsed = mutableSetOf<Int>()

    fun onCodeChange(newCode: TextFieldValue) {
        updateCode { newCode }
    }

    fun insertSymbol(symbol: String) {
        updateCode { insertTextAtCursor(it, symbol) }
    }

    fun insertTab() {
        updateCode { insertTextAtCursor(it, "    ") }
    }

    /**
     * Applies an edit to the code. Any change to the text clears a pass, so "Complete" only ever
     * follows a check of the exact source on screen. Cursor-only moves keep it.
     */
    private fun updateCode(edit: (TextFieldValue) -> TextFieldValue) {
        _uiState.update { current ->
            val newCode = edit(current.code)
            if (newCode.text == current.code.text) {
                current.copy(code = newCode)
            } else {
                current.copy(
                    code = newCode,
                    passedSource = null,
                    outcome = if (current.outcome is RunOutcome.Passed) null else current.outcome
                )
            }
        }
    }

    fun revealNextHint() {
        _uiState.update { current ->
            if (current.revealedHintsCount < current.hints.size) {
                current.copy(revealedHintsCount = current.revealedHintsCount + 1)
            } else {
                current
            }
        }
    }

    fun runCode() {
        val currentCode = _uiState.value.code.text
        _uiState.update { it.copy(isRunning = true, outcome = null) }

        scope.launch {
            val outcome = try {
                codeRunner.run(
                    files = listOf(SourceFile(name = "Solution.kt", text = currentCode))
                )
            } catch (e: Exception) {
                RunOutcome.Unavailable(reason = e.message ?: "Run execution failed")
            }

            _uiState.update {
                it.copy(isRunning = false, outcome = outcome)
            }
        }
    }

    /**
     * Runs the hidden tests on the current source. Every graded check (a pass, a test failure or a
     * compile error) is recorded once; a check that could not run (timeout or unavailable runner) is not.
     */
    fun checkCode() {
        val state = _uiState.value
        if (state.isChecking || state.isRunning) return
        val checkedSource = state.code.text
        // Set before launching, so a second tap while this check runs is ignored.
        _uiState.update { it.copy(isChecking = true, outcome = null, passedSource = null) }

        scope.launch {
            val outcome = try {
                codeRunner.test(
                    files = listOf(SourceFile(name = "Solution.kt", text = checkedSource)),
                    tests = listOf(SourceFile(name = "SolutionTest.kt", text = exercise.hiddenTests))
                )
            } catch (e: Exception) {
                RunOutcome.Unavailable(reason = e.message ?: "Test grading failed")
            }

            val passed = outcome is RunOutcome.Passed
            if (passed || outcome is RunOutcome.TestsFailed || outcome is RunOutcome.CompileError) {
                progressRecorder.record(
                    ExerciseAttempt(
                        attemptId = newAttemptId(),
                        exerciseId = exercise.id,
                        correct = passed,
                        aiHintsUsed = aiHintsUsed.toList()
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isChecking = false,
                    outcome = outcome,
                    // A pass counts only for the source that was checked; an edit since then clears it.
                    passedSource = if (passed && it.code.text == checkedSource) checkedSource else null
                )
            }
        }
    }

    fun askAiExplainMistake() {
        val tutor = aiTutor ?: return
        if (isInterviewMode || !hasApiKey) return

        val currentCode = _uiState.value.code.text
        _uiState.update {
            it.copy(
                aiDialogState = AiDialogState(
                    title = "Explain My Mistake",
                    isLoading = true
                )
            )
        }

        scope.launch {
            val result = tutor.explainMistake(
                exercise = exercise,
                userAnswer = currentCode,
                expectedResult = exercise.referenceSolution
            )
            handleAiResult(result, title = "Explain My Mistake")
        }
    }

    fun askAiHint(level: Int = 1) {
        val tutor = aiTutor ?: return
        if (isInterviewMode || !hasApiKey) return

        val currentLevel = level.coerceIn(1, 3)
        _uiState.update {
            it.copy(
                aiDialogState = AiDialogState(
                    title = "AI Hint (Level $currentLevel)",
                    isLoading = true
                )
            )
        }

        scope.launch {
            val result = tutor.hint(exercise = exercise, level = currentLevel)
            if (result is AiResult.Success) {
                aiHintsUsed.add(currentLevel)
                _uiState.update { it.copy(aiHintsUsed = aiHintsUsed.toSet()) }
            }
            handleAiResult(result, title = "AI Hint (Level $currentLevel)")
        }
    }

    fun askAiReview() {
        val tutor = aiTutor ?: return
        if (isInterviewMode || !hasApiKey) return

        val currentCode = _uiState.value.code.text
        _uiState.update {
            it.copy(
                aiDialogState = AiDialogState(
                    title = "AI Code Review",
                    isLoading = true
                )
            )
        }

        scope.launch {
            val result = tutor.reviewCode(exercise = exercise, code = currentCode)
            handleAiResult(result, title = "AI Code Review")
        }
    }

    private fun handleAiResult(result: AiResult<String>, title: String) {
        _uiState.update { current ->
            when (result) {
                is AiResult.Success -> {
                    current.copy(
                        aiDialogState = AiDialogState(
                            title = title,
                            content = result.data,
                            isLoading = false,
                            isError = false
                        )
                    )
                }
                is AiResult.Error -> {
                    current.copy(
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
    }

    fun dismissAiDialog() {
        _uiState.update { it.copy(aiDialogState = null) }
    }

    fun dismissOutcome() {
        _uiState.update { it.copy(outcome = null) }
    }
}
