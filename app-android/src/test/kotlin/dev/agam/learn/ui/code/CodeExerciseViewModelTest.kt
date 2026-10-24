package dev.agam.learn.ui.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.model.Exercise
import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.Diagnostic
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile
import dev.agam.learn.runner.TestFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CodeExerciseViewModelTest {

    private lateinit var fakeRunner: FakeCodeRunner
    private lateinit var fakeAiTutor: FakeAiTutor
    private lateinit var sampleExercise: Exercise.WriteFunction
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        fakeRunner = FakeCodeRunner()
        fakeAiTutor = FakeAiTutor()
        sampleExercise = Exercise.WriteFunction(
            id = "test-exercise-1",
            prompt = "Write a function `sum(a: Int, b: Int): Int`",
            starterCode = "fun sum(a: Int, b: Int): Int = TODO()",
            hiddenTests = "class Test { @Test fun t() { assertEquals(3, sum(1, 2)) } }",
            referenceSolution = "fun sum(a: Int, b: Int): Int = a + b",
            hints = listOf("Use + operator", "Return expression directly")
        )
    }

    @Test
    fun testInitialState() {
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            coroutineScope = testScope
        )

        val state = viewModel.uiState.value
        assertEquals("test-exercise-1", state.exerciseId)
        assertEquals("fun sum(a: Int, b: Int): Int = TODO()", state.code.text)
        assertEquals(2, state.hints.size)
        assertEquals(0, state.revealedHintsCount)
        assertFalse(state.isRunning)
        assertFalse(state.isChecking)
        assertNull(state.outcome)
        assertTrue(state.isAiAvailable)
        assertFalse(state.isInterviewMode)
    }

    @Test
    fun testCodeEditingAndSymbolInsertion() {
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        viewModel.onCodeChange(TextFieldValue("val x = 1", TextRange(9)))
        assertEquals("val x = 1", viewModel.uiState.value.code.text)

        // Insert symbol at end
        viewModel.insertSymbol("{")
        assertEquals("val x = 1{", viewModel.uiState.value.code.text)

        // Insert Tab (4 spaces)
        viewModel.insertTab()
        assertEquals("val x = 1{    ", viewModel.uiState.value.code.text)
    }

    @Test
    fun testHintsProgression() {
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        assertEquals(0, viewModel.uiState.value.revealedHintsCount)

        viewModel.revealNextHint()
        assertEquals(1, viewModel.uiState.value.revealedHintsCount)

        viewModel.revealNextHint()
        assertEquals(2, viewModel.uiState.value.revealedHintsCount)

        // Clamped to hints list size
        viewModel.revealNextHint()
        assertEquals(2, viewModel.uiState.value.revealedHintsCount)
    }

    @Test
    fun testRunCodeSuccessTransition() {
        fakeRunner.runResult = RunOutcome.Passed(testsRun = 1, stdout = "Execution output")

        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        viewModel.runCode()

        val state = viewModel.uiState.value
        assertFalse(state.isRunning)
        assertNotNull(state.outcome)
        assertTrue(state.outcome is RunOutcome.Passed)
        val passed = state.outcome as RunOutcome.Passed
        assertEquals(1, passed.testsRun)
        assertEquals("Execution output", passed.stdout)
    }

    @Test
    fun testRunCodeCompileErrorTransition() {
        fakeRunner.runResult = RunOutcome.CompileError(
            diagnostics = listOf(Diagnostic(1, 5, "ERROR", "Unresolved reference"))
        )

        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        viewModel.runCode()

        val state = viewModel.uiState.value
        assertFalse(state.isRunning)
        assertTrue(state.outcome is RunOutcome.CompileError)
        val error = state.outcome as RunOutcome.CompileError
        assertEquals(1, error.diagnostics.size)
        assertEquals("Unresolved reference", error.diagnostics[0].message)
    }

    @Test
    fun testCheckCodeTestsFailedTransition() {
        fakeRunner.testResult = RunOutcome.TestsFailed(
            failures = listOf(TestFailure("testSum", "expected 3 but was 0")),
            stdout = ""
        )

        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        viewModel.checkCode()

        val state = viewModel.uiState.value
        assertFalse(state.isChecking)
        assertTrue(state.outcome is RunOutcome.TestsFailed)
        val failed = state.outcome as RunOutcome.TestsFailed
        assertEquals(1, failed.failures.size)
        assertEquals("expected 3 but was 0", failed.failures[0].message)
    }

    @Test
    fun testCheckCodeTestsPassedTransition() {
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 3, stdout = "")

        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        viewModel.checkCode()

        val state = viewModel.uiState.value
        assertFalse(state.isChecking)
        assertTrue(state.outcome is RunOutcome.Passed)
        assertEquals(3, (state.outcome as RunOutcome.Passed).testsRun)
    }

    @Test
    fun testAskAiExplainMistakeSuccess() {
        fakeAiTutor.explainResponse = AiResult.Success("Check operator precedence.")

        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            coroutineScope = testScope
        )

        viewModel.askAiExplainMistake()

        val dialog = viewModel.uiState.value.aiDialogState
        assertNotNull(dialog)
        assertEquals("Explain My Mistake", dialog?.title)
        assertEquals("Check operator precedence.", dialog?.content)
        assertFalse(dialog!!.isError)
        assertFalse(dialog.isLoading)

        viewModel.dismissAiDialog()
        assertNull(viewModel.uiState.value.aiDialogState)
    }

    @Test
    fun testAskAiDisabledInInterviewMode() {
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            aiTutor = fakeAiTutor,
            isInterviewMode = true,
            coroutineScope = testScope
        )

        viewModel.askAiExplainMistake()
        assertNull(viewModel.uiState.value.aiDialogState)

        viewModel.askAiHint()
        assertNull(viewModel.uiState.value.aiDialogState)

        viewModel.askAiReview()
        assertNull(viewModel.uiState.value.aiDialogState)
    }

    @Test
    fun testDismissOutcome() {
        fakeRunner.runResult = RunOutcome.Timeout(seconds = 10)
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        viewModel.runCode()
        assertNotNull(viewModel.uiState.value.outcome)

        viewModel.dismissOutcome()
        assertNull(viewModel.uiState.value.outcome)
    }

    private class FakeCodeRunner : CodeRunner {
        var runResult: RunOutcome = RunOutcome.Passed(1, "")
        var testResult: RunOutcome = RunOutcome.Passed(1, "")

        override suspend fun run(files: List<SourceFile>): RunOutcome = runResult
        override suspend fun test(files: List<SourceFile>, tests: List<SourceFile>): RunOutcome = testResult
        override suspend fun diagnostics(files: List<SourceFile>): List<Diagnostic> = emptyList()
    }

    private class FakeAiTutor : AiTutor {
        var explainResponse: AiResult<String> = AiResult.Success("Mock explanation")
        var hintResponse: AiResult<String> = AiResult.Success("Mock hint")
        var reviewResponse: AiResult<String> = AiResult.Success("Mock review")

        override suspend fun explainMistake(
            exercise: Exercise,
            userAnswer: String,
            expectedResult: String
        ): AiResult<String> = explainResponse

        override suspend fun hint(exercise: Exercise, level: Int): AiResult<String> = hintResponse

        override suspend fun generateCards(topic: String, count: Int): AiResult<List<Exercise>> =
            AiResult.Success(emptyList())

        override suspend fun reviewCode(exercise: Exercise, code: String): AiResult<String> =
            reviewResponse
    }
}
