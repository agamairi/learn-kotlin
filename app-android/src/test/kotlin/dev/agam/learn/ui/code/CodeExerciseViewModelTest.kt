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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import kotlinx.coroutines.runBlocking
import org.junit.Test
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.courseOf
import dev.agam.learn.fake.progressRecorderFor

class CodeExerciseViewModelTest {

    private lateinit var fakeRunner: FakeCodeRunner
    private lateinit var fakeAiTutor: FakeAiTutor
    private lateinit var sampleExercise: Exercise.WriteFunction
    private lateinit var progress: FakeProgressRepository
    private lateinit var recorder: ProgressRecorder

    private fun storedResults() = runBlocking { progress.getExerciseResults() }

    private fun storedActivities() = runBlocking { progress.getDailyActivities() }
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
        progress = FakeProgressRepository()
        recorder = progressRecorderFor(progress, listOf(courseOf(sampleExercise)))
    }

    @Test
    fun testInitialState() {
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            hasApiKey = true,
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
        assertTrue(state.hasApiKey)
        assertFalse(state.isInterviewMode)
    }

    @Test
    fun testCodeEditingAndSymbolInsertion() {
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            hasApiKey = true,
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
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = true,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.askAiExplainMistake()
        assertNull(viewModel.uiState.value.aiDialogState)

        viewModel.askAiHint()
        assertNull(viewModel.uiState.value.aiDialogState)

        viewModel.askAiReview()
        assertNull(viewModel.uiState.value.aiDialogState)
        assertEquals("interview mode must not reach the tutor", 0, fakeAiTutor.calls)
    }

    @Test
    fun withoutAnApiKeyTheAiControlsShowTheHintAndNeverCallTheTutor() {
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            hasApiKey = false,
            coroutineScope = testScope
        )

        // The menu is still offered (disabled with a hint), so the screen can explain what is missing.
        assertTrue(viewModel.uiState.value.isAiAvailable)
        assertFalse(viewModel.uiState.value.hasApiKey)

        viewModel.askAiExplainMistake()
        viewModel.askAiHint()
        viewModel.askAiReview()

        assertNull(viewModel.uiState.value.aiDialogState)
        assertEquals(0, fakeAiTutor.calls)
    }

    @Test
    fun withAnApiKeyTheHintControlReachesTheTutor() {
        fakeAiTutor.hintResponse = AiResult.Success("Try a single expression.")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.askAiHint()

        assertEquals(1, fakeAiTutor.calls)
        assertEquals("Try a single expression.", viewModel.uiState.value.aiDialogState?.content)
    }

    @Test
    fun testDismissOutcome() {
        fakeRunner.runResult = RunOutcome.Timeout(seconds = 10)
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )

        viewModel.runCode()
        assertNotNull(viewModel.uiState.value.outcome)

        viewModel.dismissOutcome()
        assertNull(viewModel.uiState.value.outcome)
    }

    @Test
    fun aPassedCheckEnablesCompleteOnlyForTheSourceThatPassed() {
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )

        viewModel.checkCode()

        assertTrue(viewModel.uiState.value.canComplete)
    }

    @Test
    fun editingAfterAPassClearsThePassAndTheCompleteOffer() {
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        viewModel.checkCode()

        viewModel.onCodeChange(TextFieldValue("fun sum(a: Int, b: Int): Int = a - b"))

        val state = viewModel.uiState.value
        assertFalse(state.canComplete)
        assertNull(state.outcome)
        assertNull(state.passedSource)
    }

    @Test
    fun aCursorOnlyMoveKeepsThePass() {
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        viewModel.checkCode()

        viewModel.onCodeChange(viewModel.uiState.value.code.copy(selection = TextRange(0)))

        assertTrue(viewModel.uiState.value.canComplete)
    }

    @Test
    fun aRunThatPassesDoesNotEnableComplete() {
        fakeRunner.runResult = RunOutcome.Passed(testsRun = 0, stdout = "3")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )

        viewModel.runCode()

        assertTrue(viewModel.uiState.value.outcome is RunOutcome.Passed)
        assertFalse(viewModel.uiState.value.canComplete)
    }

    @Test
    fun aFirstTryPassIsRecordedForTwentyFiveXp() {
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )

        viewModel.checkCode()

        assertEquals(25, storedResults().single().xpAwarded)
        assertEquals("lesson-test", storedResults().single().lessonId)
    }

    @Test
    fun aPassAfterAFailedCheckIsRecordedForFifteenXp() {
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        fakeRunner.testResult = RunOutcome.TestsFailed(
            failures = listOf(TestFailure("testSum", "expected 3 but was 0")),
            stdout = ""
        )
        viewModel.checkCode()
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")

        viewModel.checkCode()

        val results = storedResults()
        assertEquals(listOf(false, true), results.map { it.correct })
        assertEquals(listOf(0, 15), results.map { it.xpAwarded })
    }

    @Test
    fun usingAiHintLevelOneReducesRecordedXpToTwentyThree() {
        fakeAiTutor.hintResponse = AiResult.Success("Nudge hint")
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.askAiHint(1)
        viewModel.checkCode()

        assertEquals(23, storedResults().single().xpAwarded)
    }

    @Test
    fun usingAiHintLevelTwoReducesRecordedXpToTwenty() {
        fakeAiTutor.hintResponse = AiResult.Success("Approach hint")
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.askAiHint(2)
        viewModel.checkCode()

        assertEquals(20, storedResults().single().xpAwarded)
    }

    @Test
    fun usingMultipleAiHintsReducesRecordedXpSequentially() {
        fakeAiTutor.hintResponse = AiResult.Success("Hint")
        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.askAiHint(1) // -2 XP
        viewModel.askAiHint(2) // -5 XP
        viewModel.askAiHint(3) // -10 XP
        viewModel.checkCode()

        // 25 - 2 - 5 - 10 = 8 XP
        assertEquals(8, storedResults().single().xpAwarded)
    }

    @Test
    fun retryWithAllHintsNeverReducesRecordedXpBelowZero() {
        fakeAiTutor.hintResponse = AiResult.Success("Hint")
        fakeRunner.testResult = RunOutcome.TestsFailed(
            failures = listOf(TestFailure("testSum", "expected 3 but was 0")),
            stdout = ""
        )
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = fakeAiTutor,
            isInterviewMode = false,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.checkCode() // Fails first try (0 XP)
        viewModel.askAiHint(1) // -2 XP
        viewModel.askAiHint(2) // -5 XP
        viewModel.askAiHint(3) // -10 XP

        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        viewModel.checkCode() // Passes on retry (base 15 - 17 -> 0 XP)

        val results = storedResults()
        assertEquals(listOf(false, true), results.map { it.correct })
        assertEquals(listOf(0, 0), results.map { it.xpAwarded })
    }

    @Test
    fun aCheckThatCannotRunIsNotRecorded() {
        fakeRunner.testResult = RunOutcome.Timeout(seconds = 10)
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )

        viewModel.checkCode()

        assertTrue(storedResults().isEmpty())
    }

    @Test
    fun onlyGradedChecksEmitOneShotFeedbackAndRunOrInfrastructureResultsStayQuiet() = runBlocking {
        val feedback = mutableListOf<Boolean>()
        val viewModel = CodeExerciseViewModel(
            exercise = sampleExercise,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        val collection = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.feedbackEvents.collect { feedback += it }
        }

        fakeRunner.testResult = RunOutcome.Passed(testsRun = 1, stdout = "")
        viewModel.checkCode()
        assertEquals(listOf(true), feedback)
        assertTrue(viewModel.feedbackEvents.replayCache.isEmpty())

        // Re-rendering/restoring the existing outcome or editing it does not emit another event.
        viewModel.onCodeChange(TextFieldValue("fun sum(a: Int, b: Int): Int = a - b"))
        assertEquals(listOf(true), feedback)

        // A visible failure from plain Run is diagnostic output, not a graded answer.
        fakeRunner.runResult = RunOutcome.TestsFailed(
            failures = listOf(TestFailure("smoke", "exit code 1")),
            stdout = ""
        )
        viewModel.runCode()
        assertEquals(listOf(true), feedback)

        fakeRunner.testResult = RunOutcome.TestsFailed(
            failures = listOf(TestFailure("testSum", "expected 3 but was 0")),
            stdout = ""
        )
        viewModel.checkCode()
        fakeRunner.testResult = RunOutcome.CompileError(
            diagnostics = listOf(Diagnostic(1, 1, "ERROR", "Unresolved reference"))
        )
        viewModel.checkCode()
        assertEquals(listOf(true, false, false), feedback)

        fakeRunner.testResult = RunOutcome.Timeout(seconds = 10)
        viewModel.checkCode()
        fakeRunner.testResult = RunOutcome.Unavailable(reason = "Compiler offline")
        viewModel.checkCode()
        assertEquals(listOf(true, false, false), feedback)

        collection.cancel()
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
        var calls = 0

        override suspend fun explainMistake(
            exercise: Exercise,
            userAnswer: String,
            expectedResult: String
        ): AiResult<String> {
            calls++
            return explainResponse
        }

        override suspend fun hint(exercise: Exercise, level: Int): AiResult<String> {
            calls++
            return hintResponse
        }

        override suspend fun generateCards(topic: String, count: Int): AiResult<List<Exercise>> =
            AiResult.Success(emptyList())

        override suspend fun reviewCode(exercise: Exercise, code: String): AiResult<String> {
            calls++
            return reviewResponse
        }
    }
}
