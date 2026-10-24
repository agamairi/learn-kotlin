package dev.agam.learn.ui.code

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
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
    private val aiTutor: AiTutor? = null,
    val isInterviewMode: Boolean = false,
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
            isInterviewMode = isInterviewMode
        )
    )
    val uiState: StateFlow<CodeExerciseUiState> = _uiState.asStateFlow()

    fun onCodeChange(newCode: TextFieldValue) {
        _uiState.update { it.copy(code = newCode) }
    }

    fun insertSymbol(symbol: String) {
        _uiState.update { current ->
            current.copy(code = insertTextAtCursor(current.code, symbol))
        }
    }

    fun insertTab() {
        _uiState.update { current ->
            current.copy(code = insertTextAtCursor(current.code, "    "))
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

    fun checkCode() {
        val currentCode = _uiState.value.code.text
        _uiState.update { it.copy(isChecking = true, outcome = null) }

        scope.launch {
            val outcome = try {
                codeRunner.test(
                    files = listOf(SourceFile(name = "Solution.kt", text = currentCode)),
                    tests = listOf(SourceFile(name = "SolutionTest.kt", text = exercise.hiddenTests))
                )
            } catch (e: Exception) {
                RunOutcome.Unavailable(reason = e.message ?: "Test grading failed")
            }

            _uiState.update {
                it.copy(isChecking = false, outcome = outcome)
            }
        }
    }

    fun askAiExplainMistake() {
        val tutor = aiTutor ?: return
        if (isInterviewMode) return

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

    fun askAiHint() {
        val tutor = aiTutor ?: return
        if (isInterviewMode) return

        val currentLevel = (_uiState.value.revealedHintsCount + 1).coerceIn(1, 3)
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
            handleAiResult(result, title = "AI Hint (Level $currentLevel)")
        }
    }

    fun askAiReview() {
        val tutor = aiTutor ?: return
        if (isInterviewMode) return

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
