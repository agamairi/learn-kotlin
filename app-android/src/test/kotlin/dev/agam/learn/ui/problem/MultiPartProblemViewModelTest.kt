package dev.agam.learn.ui.problem

import androidx.compose.ui.text.input.TextFieldValue
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.model.FaqItem
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.model.Exercise
import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.Diagnostic
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultiPartProblemViewModelTest {

    private lateinit var fakeRunner: FakeCodeRunner
    private lateinit var problem: Exercise.MultiPartProblem
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        fakeRunner = FakeCodeRunner()
        problem = Exercise.MultiPartProblem(
            id = "ledger-system",
            prompt = "Ledger Processing System",
            story = "Process financial ledgers with transaction records.",
            parts = listOf(
                ProblemPart(
                    id = "part1",
                    prompt = "Implement parse()",
                    starterCode = "fun parse(): List<Int> = TODO()",
                    hiddenTests = "class Part1Test",
                    referenceSolution = "fun parse() = listOf(1, 2)",
                    expected = "List of 1, 2"
                ),
                ProblemPart(
                    id = "part2",
                    prompt = "Implement calculateBalance()",
                    starterCode = null,
                    hiddenTests = "class Part2Test",
                    referenceSolution = "fun calculateBalance() = 100",
                    expected = "100"
                )
            ),
            faq = listOf(FaqItem("Are amounts signed?", "Amounts are unsigned cents."))
        )
    }

    @Test
    fun testInitialState() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            isInterviewModeDefault = true,
            coroutineScope = testScope
        )

        val state = viewModel.uiState.value
        assertEquals("ledger-system", state.problemId)
        assertEquals(0, state.currentPartIndex)
        assertEquals(1, state.unlockedPartCount)
        assertTrue(state.isInterviewMode)
        assertEquals("fun parse(): List<Int> = TODO()", state.currentCode.text)
    }

    @Test
    fun testPartProgressionLockedUntilPass() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        // Attempt reveal before passing -> should not unlock
        viewModel.revealNextPart()
        assertEquals(1, viewModel.uiState.value.unlockedPartCount)
        assertEquals(0, viewModel.uiState.value.currentPartIndex)

        // Run check that passes
        fakeRunner.testOutcome = RunOutcome.Passed(testsRun = 2, stdout = "")
        viewModel.checkPart()

        assertTrue(viewModel.uiState.value.isCurrentPartPassed)

        // Now reveal next part -> unlocks part 2
        viewModel.revealNextPart()
        assertEquals(2, viewModel.uiState.value.unlockedPartCount)
        assertEquals(1, viewModel.uiState.value.currentPartIndex)
    }

    @Test
    fun testInterviewModeToggle() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            isInterviewModeDefault = false,
            coroutineScope = testScope
        )

        assertFalse(viewModel.uiState.value.isInterviewMode)
        viewModel.toggleInterviewMode(true)
        assertTrue(viewModel.uiState.value.isInterviewMode)
        viewModel.toggleInterviewMode(false)
        assertFalse(viewModel.uiState.value.isInterviewMode)
    }

    @Test
    fun testTimerControls() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            coroutineScope = testScope
        )

        assertEquals(45 * 60, viewModel.uiState.value.timerSeconds)
        assertFalse(viewModel.uiState.value.isTimerRunning)

        viewModel.toggleTimer()
        assertTrue(viewModel.uiState.value.isTimerRunning)

        viewModel.toggleTimer()
        assertFalse(viewModel.uiState.value.isTimerRunning)

        viewModel.resetTimer()
        assertEquals(45 * 60, viewModel.uiState.value.timerSeconds)
        assertFalse(viewModel.uiState.value.isTimerRunning)
    }

    @Test
    fun withoutAnApiKeyTheAiControlsShowTheHintAndNeverCallTheTutor() {
        val tutor = FakeAiTutor()
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            aiTutor = tutor,
            isInterviewModeDefault = false,
            hasApiKey = false,
            coroutineScope = testScope
        )

        assertTrue(viewModel.uiState.value.isAiAvailable)
        assertFalse(viewModel.uiState.value.hasApiKey)

        viewModel.askAiExplainMistake()
        viewModel.askAiHint()
        viewModel.askAiReview()

        assertNull(viewModel.uiState.value.aiDialogState)
        assertEquals(0, tutor.calls)
    }

    @Test
    fun interviewModeHidesTheAiTutorEvenWithAKey() {
        val tutor = FakeAiTutor()
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            aiTutor = tutor,
            isInterviewModeDefault = true,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.askAiHint()

        assertNull(viewModel.uiState.value.aiDialogState)
        assertEquals(0, tutor.calls)
    }

    @Test
    fun withAnApiKeyTheHintControlReachesTheTutor() {
        val tutor = FakeAiTutor()
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            aiTutor = tutor,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.askAiHint()

        assertEquals(1, tutor.calls)
        assertEquals("Mock hint", viewModel.uiState.value.aiDialogState?.content)
    }

    private class FakeAiTutor : AiTutor {
        var calls = 0

        override suspend fun explainMistake(exercise: Exercise, userAnswer: String, expectedResult: String): AiResult<String> {
            calls++
            return AiResult.Success("Mock explanation")
        }

        override suspend fun hint(exercise: Exercise, level: Int): AiResult<String> {
            calls++
            return AiResult.Success("Mock hint")
        }

        override suspend fun generateCards(topic: String, count: Int): AiResult<List<Exercise>> =
            AiResult.Success(emptyList())

        override suspend fun reviewCode(exercise: Exercise, code: String): AiResult<String> {
            calls++
            return AiResult.Success("Mock review")
        }
    }

    private class FakeCodeRunner : CodeRunner {
        var testOutcome: RunOutcome = RunOutcome.Passed(1, "")
        override suspend fun run(files: List<SourceFile>): RunOutcome = RunOutcome.Passed(1, "")
        override suspend fun test(files: List<SourceFile>, tests: List<SourceFile>): RunOutcome = testOutcome
        override suspend fun diagnostics(files: List<SourceFile>): List<Diagnostic> = emptyList()
    }
}
