package dev.agam.learn.ui.problem

import androidx.compose.ui.text.input.TextFieldValue
import dev.agam.learn.ai.AiProvider
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.ai.DynamicAiTutor
import dev.agam.learn.data.AppSettings
import dev.agam.learn.data.SecretStore
import dev.agam.learn.fake.FakeSettingsRepository
import dev.agam.learn.model.Blank
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.model.FaqItem
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.model.Exercise
import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.Diagnostic
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import dev.agam.learn.ai.createAiHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.ControlledProgressRepository
import dev.agam.learn.fake.courseOf
import dev.agam.learn.fake.progressRecorderFor

class MultiPartProblemViewModelTest {

    private lateinit var fakeRunner: FakeCodeRunner
    private lateinit var problem: Exercise.MultiPartProblem
    private lateinit var progress: FakeProgressRepository
    private lateinit var recorder: ProgressRecorder

    private fun storedResults() = runBlocking { progress.getExerciseResults() }

    private fun storedActivities() = runBlocking { progress.getDailyActivities() }
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    private suspend fun awaitCheckFinished(viewModel: MultiPartProblemViewModel) {
        withTimeout(5_000) {
            while (viewModel.uiState.value.isChecking) delay(1)
        }
    }

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
        progress = FakeProgressRepository()
        recorder = progressRecorderFor(progress, listOf(courseOf(problem)))
    }

    private fun guidedSinglePartProblem(problemId: String, partId: String) = Exercise.MultiPartProblem(
        id = problemId,
        prompt = "Return the payment total",
        story = "Build the payment total calculation.",
        parts = listOf(
            ProblemPart(
                id = partId,
                prompt = "Return 42",
                starterCode = "fun total(): Int = TODO()",
                hiddenTests = "class Test",
                referenceSolution = "fun total(): Int = 42",
                expected = "42",
                guided = CodePuzzle(
                    template = "fun total(): Int = ____1",
                    blanks = listOf(Blank("____1", listOf("42"), options = listOf("42", "0", "1", "2")))
                )
            )
        )
    )

    @Test
    fun testInitialState() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
    fun multipartPartsAndCompletionWaitForTheLessonSettlement() = runBlocking {
        val runId = "multipart-lesson-run"
        assertTrue(recorder.beginLessonRun("lesson-test", runId))
        fakeRunner.testOutcome = RunOutcome.Passed(testsRun = 2, stdout = "")
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            lessonRunId = runId,
            coroutineScope = testScope
        )

        viewModel.checkPart()
        viewModel.revealNextPart()
        viewModel.checkPart()

        assertTrue(progress.getExerciseResults().isEmpty())
        assertTrue(progress.getDailyActivities().isEmpty())
        assertEquals(100, recorder.lessonRunXp(runId))
        assertEquals(
            dev.agam.learn.data.LessonRunSettlementOutcome.Settled(100),
            recorder.settleLessonRun("lesson-test", runId)
        )
        assertEquals(setOf("part1", "part2", "ledger-system"), progress.getExerciseResults().map { it.exerciseId }.toSet())
        assertEquals(1, progress.getDailyActivities().single().exercisesCompleted)
    }

    @Test
    fun testInterviewModeToggle() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
            progressRecorder = recorder,
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
            progressRecorder = recorder,
            aiTutor = tutor,
            hasApiKey = true,
            coroutineScope = testScope
        )

        viewModel.askAiHint()

        assertEquals(1, tutor.calls)
        assertEquals("Mock hint", viewModel.uiState.value.aiDialogState?.content)
    }

    @Test
    fun sessionInterviewToggleGovernsAiAvailabilityEvenIfSavedDefaultIsOn() = kotlinx.coroutines.runBlocking {
        val settingsRepo = FakeSettingsRepository(
            AppSettings(selectedAiProvider = AiProvider.ANTHROPIC, interviewMode = true)
        )
        val secretStore = object : SecretStore {
            override suspend fun get(): String? = "test-key"
            override suspend fun set(value: String) {}
            override suspend fun delete() {}
        }
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"content":[{"type":"text","text":"Session hint text"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = createAiHttpClient(mockEngine)
        val dynamicTutor = DynamicAiTutor(
            settingsRepository = settingsRepo,
            secretStore = secretStore,
            httpClient = httpClient
        )

        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = dynamicTutor,
            isInterviewModeDefault = true,
            hasApiKey = true,
            coroutineScope = this
        )

        // Session toggle is initially on: tutor is not called
        viewModel.askAiHint()
        assertNull(viewModel.uiState.value.aiDialogState)

        // Learner switches session toggle off:
        viewModel.toggleInterviewMode(false)
        assertFalse(viewModel.uiState.value.isInterviewMode)

        // The session toggle governs: tutor responds and does NOT refuse with interview mode error
        viewModel.askAiHint()

        var dialogState = viewModel.uiState.value.aiDialogState
        while (dialogState == null || dialogState.isLoading) {
            kotlinx.coroutines.delay(20)
            dialogState = viewModel.uiState.value.aiDialogState
        }

        assertNotNull(dialogState)
        assertFalse("Tutor must not report an error: ${dialogState.content}", dialogState.isError)
        assertEquals("Session hint text", dialogState.content)
    }

    @Test
    fun eachPassedPartPaysFiftyXpOnceAndTheProblemCompletesSeparately() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        fakeRunner.testOutcome = RunOutcome.Passed(testsRun = 1, stdout = "")

        viewModel.checkPart()
        viewModel.revealNextPart()
        viewModel.checkPart()

        val results = storedResults()
        assertEquals(listOf("part1", "part2", "ledger-system"), results.map { it.exerciseId })
        assertEquals(listOf(50, 50, 0), results.map { it.xpAwarded })
        assertTrue(results.all { it.lessonId == "lesson-test" })
        assertEquals(100, storedActivities().single().xpEarned)
        assertTrue(viewModel.uiState.value.allPartsPassed)
    }

    @Test
    fun aPartIsNotPaidAgainOnARecheck() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        fakeRunner.testOutcome = RunOutcome.Passed(testsRun = 1, stdout = "")

        viewModel.checkPart()
        viewModel.checkPart()

        assertEquals(50, storedActivities().single().xpEarned)
    }

    @Test
    fun onlyGradedPartChecksEmitOneShotFeedback() = runBlocking {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        val feedback = mutableListOf<Boolean>()
        val collection = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.feedbackEvents.collect { feedback += it }
        }

        fakeRunner.runOutcome = RunOutcome.TestsFailed(
            failures = emptyList(),
            stdout = "plain run output"
        )
        viewModel.runCode()
        assertTrue(feedback.isEmpty())

        fakeRunner.testOutcome = RunOutcome.Passed(testsRun = 1, stdout = "")
        viewModel.checkPart()
        assertEquals(listOf(true), feedback)
        viewModel.revealNextPart()

        fakeRunner.testOutcome = RunOutcome.TestsFailed(
            failures = emptyList(),
            stdout = ""
        )
        viewModel.checkPart()
        assertEquals(listOf(true, false), feedback)

        fakeRunner.testOutcome = RunOutcome.Timeout(seconds = 10)
        viewModel.checkPart()
        fakeRunner.testOutcome = RunOutcome.Unavailable(reason = "Compiler offline")
        viewModel.checkPart()

        assertEquals(listOf(true, false), feedback)
        assertTrue(viewModel.feedbackEvents.replayCache.isEmpty())
        collection.cancel()
    }

    @Test
    fun editingAPassedPartClearsItsPassAndTheProblemCompletion() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        fakeRunner.testOutcome = RunOutcome.Passed(testsRun = 1, stdout = "")
        viewModel.checkPart()
        assertTrue(viewModel.uiState.value.isCurrentPartPassed)

        viewModel.onCodeChange(TextFieldValue("fun parse(): List<Int> = emptyList()"))

        assertFalse(viewModel.uiState.value.isCurrentPartPassed)
        assertFalse(viewModel.uiState.value.allPartsPassed)
        assertNull(viewModel.uiState.value.currentOutcome)
    }

    @Test
    fun aMockTimerRefusesEveryAiActionEvenWithAKeyAndInterviewModeOff() {
        val tutor = FakeAiTutor()
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            aiTutor = tutor,
            hasApiKey = true,
            coroutineScope = testScope
        )
        viewModel.toggleTimer()
        assertTrue(viewModel.uiState.value.isTimerRunning)

        viewModel.askAiHint()
        viewModel.askAiExplainMistake()
        viewModel.askAiReview()

        assertEquals(0, tutor.calls)
        assertNull(viewModel.uiState.value.aiDialogState)
        viewModel.toggleTimer()
    }

    @Test
    fun theInterviewToggleIsLockedWhileTheTimerRunsAndFreeAfterwards() {
        val viewModel = MultiPartProblemViewModel(
            problem = problem,
            codeRunner = fakeRunner,
            progressRecorder = recorder,
            coroutineScope = testScope
        )
        viewModel.toggleTimer()

        viewModel.toggleInterviewMode(true)
        assertFalse(viewModel.uiState.value.isInterviewMode)

        viewModel.toggleTimer()
        viewModel.toggleInterviewMode(true)
        assertTrue(viewModel.uiState.value.isInterviewMode)
    }

    @Test
    fun testOfflineGuidedMultipartProgressionAndParentCompletionMarker() = runBlocking {
        val runId = "multipart-guided-run"
        val guidedProblem = Exercise.MultiPartProblem(
            id = "guided-problem-1",
            prompt = "Payment Pipeline",
            story = "Process payment events offline.",
            parts = listOf(
                ProblemPart(
                    id = "p-part1",
                    prompt = "Parse payment event",
                    starterCode = "fun parse(): String = TODO()",
                    hiddenTests = "class Part1Test",
                    referenceSolution = "fun parse() = \"payment\"",
                    expected = "payment",
                    guided = CodePuzzle(
                        template = "fun parse(): String = ____1",
                        blanks = listOf(
                            Blank(
                                placeholder = "____1",
                                accepted = listOf("\"payment\""),
                                options = listOf("\"payment\"", "\"charge\"", "\"refund\"", "\"payout\"")
                            )
                        )
                    )
                ),
                ProblemPart(
                    id = "p-part2",
                    prompt = "Calculate fee",
                    starterCode = "fun fee(): Long = TODO()",
                    hiddenTests = "class Part2Test",
                    referenceSolution = "fun fee() = 30L",
                    expected = "30",
                    guided = CodePuzzle(
                        template = "fun fee(): Long = ____1",
                        blanks = listOf(
                            Blank(
                                placeholder = "____1",
                                accepted = listOf("30L"),
                                options = listOf("30L", "15L", "50L", "0L")
                            )
                        )
                    )
                )
            )
        )
        val rec = progressRecorderFor(progress, listOf(courseOf(guidedProblem)))
        assertTrue(rec.beginLessonRun("lesson-test", runId))

        val viewModel = MultiPartProblemViewModel(
            problem = guidedProblem,
            codeRunner = fakeRunner,
            progressRecorder = rec,
            lessonRunId = runId,
            coroutineScope = testScope
        )

        // Part 1 initial state: reflected code contains placeholder
        assertTrue(viewModel.uiState.value.currentCode.text.contains("« Blank 1 »"))

        // Select Part 1 option
        viewModel.selectOption("____1", "\"payment\"")
        assertEquals("fun parse(): String = \"payment\"", viewModel.uiState.value.currentCode.text)

        // Check Part 1: passes offline and awards 50 XP
        viewModel.checkPart()
        awaitCheckFinished(viewModel)
        assertTrue(viewModel.uiState.value.isCurrentPartPassed)
        assertFalse(viewModel.uiState.value.allPartsPassed)
        assertEquals(50, rec.lessonRunXp(runId))

        // Reveal Part 2
        viewModel.revealNextPart()
        assertEquals(1, viewModel.uiState.value.currentPartIndex)
        assertEquals(2, viewModel.uiState.value.unlockedPartCount)

        // Select Part 2 option
        viewModel.selectOption("____1", "30L")
        assertEquals("fun fee(): Long = 30L", viewModel.uiState.value.currentCode.text)

        // Check Part 2: passes offline, awards 50 XP (total 100), and records parent completion marker
        viewModel.checkPart()
        awaitCheckFinished(viewModel)
        assertTrue(viewModel.uiState.value.isCurrentPartPassed)
        assertTrue(viewModel.uiState.value.allPartsPassed)
        assertEquals(100, rec.lessonRunXp(runId))

        // Confirm parent completion marker was recorded into the lesson run
        assertTrue(rec.lessonRunHasPassed(runId, "guided-problem-1"))

        // Settlement commits both parts and the parent completion marker
        val settlement = rec.settleLessonRun("lesson-test", runId)
        assertEquals(dev.agam.learn.data.LessonRunSettlementOutcome.Settled(100), settlement)
        val results = progress.getExerciseResults()
        assertEquals(setOf("p-part1", "p-part2", "guided-problem-1"), results.map { it.exerciseId }.toSet())
    }

    @Test
    fun testOfflineGuidedMultipartWrongRetryAndLocking() = runBlocking {
        val guidedProblem = Exercise.MultiPartProblem(
            id = "guided-wrong-retry",
            prompt = "Payment Pipeline",
            story = "Process payment events.",
            parts = listOf(
                ProblemPart(
                    id = "part-retry",
                    prompt = "Parse payment event",
                    starterCode = "fun parse(): String = TODO()",
                    hiddenTests = "class Part1Test",
                    referenceSolution = "fun parse() = \"payment\"",
                    expected = "payment",
                    guided = CodePuzzle(
                        template = "fun parse(): String = ____1",
                        blanks = listOf(
                            Blank(
                                placeholder = "____1",
                                accepted = listOf("\"payment\""),
                                options = listOf("\"payment\"", "\"charge\"", "\"refund\"", "\"payout\"")
                            )
                        )
                    )
                )
            )
        )

        val guidedRecorder = progressRecorderFor(progress, listOf(courseOf(guidedProblem)))
        val viewModel = MultiPartProblemViewModel(
            problem = guidedProblem,
            codeRunner = fakeRunner,
            progressRecorder = guidedRecorder,
            coroutineScope = testScope
        )

        // Select WRONG option
        viewModel.selectOption("____1", "\"charge\"")
        viewModel.checkPart()
        awaitCheckFinished(viewModel)

        assertFalse(viewModel.uiState.value.isCurrentPartPassed)
        assertEquals(true, viewModel.uiState.value.partChecked[0])

        // Locked: another select while checked does not change choice
        viewModel.selectOption("____1", "\"refund\"")
        assertEquals("\"charge\"", viewModel.uiState.value.partPuzzleAnswers[0]?.get("____1"))

        // Retry unlocks selections
        viewModel.retryCurrentPart()
        assertFalse(viewModel.uiState.value.partChecked[0] == true)

        // Now select correct option
        viewModel.selectOption("____1", "\"payment\"")
        viewModel.checkPart()
        awaitCheckFinished(viewModel)

        assertTrue(viewModel.uiState.value.isCurrentPartPassed)
        assertTrue(viewModel.uiState.value.allPartsPassed)
    }

    @Test
    fun guidedPartCheckWaitsForItsRecordAndRejectsDuplicateChecksWhilePending() = runBlocking {
        val guidedProblem = guidedSinglePartProblem("guided-pending-part", "part-pending")
        val storage = FakeProgressRepository()
        val repository = ControlledProgressRepository(storage).apply {
            gatedExerciseId = "part-pending"
        }
        val progressRecorder = progressRecorderFor(repository, listOf(courseOf(guidedProblem)))
        val runId = "guided-pending-part-run"
        assertTrue(progressRecorder.beginLessonRun("lesson-test", runId))
        val viewModel = MultiPartProblemViewModel(
            problem = guidedProblem,
            codeRunner = fakeRunner,
            progressRecorder = progressRecorder,
            lessonRunId = runId,
            coroutineScope = testScope
        )
        viewModel.selectOption("____1", "42")

        viewModel.checkPart()
        withTimeout(5_000) { repository.historyReadEntered.await() }

        assertTrue(viewModel.uiState.value.isChecking)
        assertFalse(viewModel.uiState.value.isCurrentPartPassed)
        assertFalse(viewModel.uiState.value.allPartsPassed)
        viewModel.checkPart()
        viewModel.selectOption("____1", "0")
        assertEquals(1, repository.gatedHistoryReadCount)
        assertEquals("42", viewModel.uiState.value.partPuzzleAnswers[0]?.get("____1"))
        assertTrue(storage.getExerciseResults().isEmpty())

        repository.releaseHistoryRead.complete(Unit)
        awaitCheckFinished(viewModel)

        assertFalse(viewModel.uiState.value.isChecking)
        assertTrue(viewModel.uiState.value.isCurrentPartPassed)
        assertTrue(viewModel.uiState.value.allPartsPassed)
        assertEquals(50, progressRecorder.lessonRunXp(runId))
    }

    @Test
    fun finalPartCannotFinishUntilTheParentCompletionMarkerIsRecorded() = runBlocking {
        val guidedProblem = guidedSinglePartProblem("guided-pending-parent", "part-parent")
        val storage = FakeProgressRepository()
        val repository = ControlledProgressRepository(storage).apply {
            gatedExerciseId = guidedProblem.id
        }
        val progressRecorder = progressRecorderFor(repository, listOf(courseOf(guidedProblem)))
        val runId = "guided-pending-parent-run"
        assertTrue(progressRecorder.beginLessonRun("lesson-test", runId))
        val viewModel = MultiPartProblemViewModel(
            problem = guidedProblem,
            codeRunner = fakeRunner,
            progressRecorder = progressRecorder,
            lessonRunId = runId,
            coroutineScope = testScope
        )
        viewModel.selectOption("____1", "42")

        viewModel.checkPart()
        withTimeout(5_000) { repository.historyReadEntered.await() }

        assertTrue(viewModel.uiState.value.isChecking)
        assertFalse(viewModel.uiState.value.isCurrentPartPassed)
        assertFalse(viewModel.uiState.value.allPartsPassed)
        // The part is staged in memory, but the parent marker is still pending and nothing is durable.
        assertTrue(storage.getExerciseResults().isEmpty())
        viewModel.checkPart()
        viewModel.revealNextPart()
        assertEquals(1, repository.gatedHistoryReadCount)
        assertEquals(0, viewModel.uiState.value.currentPartIndex)

        repository.releaseHistoryRead.complete(Unit)
        awaitCheckFinished(viewModel)

        assertFalse(viewModel.uiState.value.isChecking)
        assertTrue(viewModel.uiState.value.isCurrentPartPassed)
        assertTrue(viewModel.uiState.value.allPartsPassed)
        assertTrue(progressRecorder.lessonRunHasPassed(runId, guidedProblem.id))
    }

    @Test
    fun testStaleRunDoesNotAdvanceOrSettle() = runBlocking {
        val activeRunId = "active-run"
        val staleRunId = "stale-run"

        val guidedProblem = Exercise.MultiPartProblem(
            id = "guided-stale-test",
            prompt = "Pipeline",
            story = "Story",
            parts = listOf(
                ProblemPart(
                    id = "stale-part-1",
                    prompt = "Prompt",
                    starterCode = "fun p() = 1",
                    hiddenTests = "class Test",
                    referenceSolution = "fun p() = 1",
                    expected = "1",
                    guided = CodePuzzle(
                        template = "fun p() = ____1",
                        blanks = listOf(Blank("____1", listOf("1"), options = listOf("1", "2", "3", "4")))
                    )
                )
            )
        )
        val rec = progressRecorderFor(progress, listOf(courseOf(guidedProblem)))
        assertTrue(rec.beginLessonRun("lesson-test", activeRunId))

        // ViewModel initialized with a stale/unregistered runId
        val viewModel = MultiPartProblemViewModel(
            problem = guidedProblem,
            codeRunner = fakeRunner,
            progressRecorder = rec,
            lessonRunId = staleRunId,
            coroutineScope = testScope
        )

        viewModel.selectOption("____1", "1")
        viewModel.checkPart()
        awaitCheckFinished(viewModel)

        // Active run was untouched
        assertEquals(0, rec.lessonRunXp(activeRunId))
        assertFalse(rec.lessonRunHasPassed(activeRunId, "guided-stale-test"))
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
        var runOutcome: RunOutcome = RunOutcome.Passed(1, "")
        override suspend fun run(files: List<SourceFile>): RunOutcome = runOutcome
        override suspend fun test(files: List<SourceFile>, tests: List<SourceFile>): RunOutcome = testOutcome
        override suspend fun diagnostics(files: List<SourceFile>): List<Diagnostic> = emptyList()
    }
}
