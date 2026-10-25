package dev.agam.learn.ui.review

import dev.agam.learn.domain.DefaultAnswerGrader
import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FixedClock
import dev.agam.learn.fake.SampleCourseData
import dev.agam.learn.fake.courseOf
import dev.agam.learn.fake.progressRecorderFor
import dev.agam.learn.model.Blank
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReviewViewModelTest {

    private lateinit var fakeRepo: FakeProgressRepository
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    private val mcExercise = Exercise.MultipleChoice(
        id = "item-mc",
        prompt = "What is Kotlin?",
        options = listOf("Language", "Animal"),
        correctIndex = 0,
        explanation = "Modern language"
    )

    @Before
    fun setup() {
        fakeRepo = FakeProgressRepository()
    }

    private fun storedResults() = runBlocking { fakeRepo.getExerciseResults() }

    private fun storedActivities() = runBlocking { fakeRepo.getDailyActivities() }

    private fun storedCard(itemId: String) = runBlocking { fakeRepo.getReviewCard(itemId) }

    private fun seed(vararg cards: ReviewCard) = runBlocking {
        cards.forEach { fakeRepo.upsertReviewCard(it) }
    }

    /** Learning day 100 (April 11, 1970) at noon UTC, which is 07:00 or 08:00 in Toronto. */
    private val day100 = FixedClock(Instant.fromEpochMilliseconds(100L * 86_400_000L + 12 * 3_600_000L))

    private fun viewModel(
        exercises: List<Exercise>,
        clock: FixedClock = day100,
        exerciseLookup: ((String) -> Exercise?)? = null
    ): ReviewViewModel {
        val course = courseOf(*exercises.toTypedArray())
        val recorder = progressRecorderFor(
            fakeRepo,
            listOf(course),
            clock = clock,
            timeZone = TimeZone.of("America/Toronto")
        )
        return ReviewViewModel(
            progressRepository = fakeRepo,
            progressRecorder = recorder,
            exerciseLookup = exerciseLookup,
            coroutineScope = testScope
        )
    }

    @Test
    fun testQueueLoading() {
        seed(
            ReviewCard("card-1", 1, 100),
            ReviewCard("card-2", 2, 100),
            ReviewCard("card-3", 3, 110)
        )

        val viewModel = viewModel(emptyList())

        val state = viewModel.uiState.value
        assertEquals(2, state.dueCards.size)
        assertEquals(1, state.boxCounts[1])
        assertEquals(1, state.boxCounts[2])
        assertEquals(1, state.boxCounts[3])
    }

    @Test
    fun cardWithoutExerciseIsLeftOutOfTheSessionNotFabricated() {
        seed(ReviewCard("missing-item", 1, 100))

        val viewModel = viewModel(emptyList(), exerciseLookup = { null })

        viewModel.startSession()

        assertFalse(viewModel.uiState.value.isInSession)
        assertTrue(viewModel.uiState.value.sessionItems.isEmpty())
    }

    @Test
    fun testSessionFlowCorrectAnswer() {
        seed(ReviewCard("item-mc", 1, 100))

        val viewModel = viewModel(listOf(mcExercise), exerciseLookup = { mcExercise })

        viewModel.startSession()
        assertTrue(viewModel.uiState.value.isInSession)

        viewModel.selectOption(0)
        viewModel.checkAnswer()

        assertTrue(viewModel.uiState.value.isAnswerChecked)
        assertTrue(viewModel.uiState.value.isAnswerCorrect)
        assertEquals(2, viewModel.uiState.value.updatedBox)
        assertEquals(10, viewModel.uiState.value.xpEarned)

        // Advance to finish
        viewModel.nextCard()
        assertTrue(viewModel.uiState.value.isSessionComplete)
    }

    @Test
    fun aDoubleTapRecordsTheAnswerOnce() {
        seed(ReviewCard("item-mc", 1, 100))
        val viewModel = viewModel(listOf(mcExercise), exerciseLookup = { mcExercise })
        viewModel.startSession()
        viewModel.selectOption(0)

        viewModel.checkAnswer()
        viewModel.checkAnswer()

        assertEquals(1, storedResults().size)
        assertEquals(10, storedActivities().single().xpEarned)
        assertEquals(1, viewModel.uiState.value.totalAnswered)
        assertEquals(10, viewModel.uiState.value.xpEarned)
    }

    @Test
    fun checkedReviewAnswersEmitOneShotCorrectAndWrongFeedbackOnceEach() = runBlocking {
        val secondExercise = mcExercise.copy(id = "item-mc-second")
        seed(
            ReviewCard("item-mc", 1, 100),
            ReviewCard("item-mc-second", 1, 100)
        )
        val viewModel = viewModel(
            listOf(mcExercise, secondExercise),
            exerciseLookup = { itemId ->
                when (itemId) {
                    mcExercise.id -> mcExercise
                    secondExercise.id -> secondExercise
                    else -> null
                }
            }
        )
        val feedback = mutableListOf<Boolean>()
        val collection = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.feedbackEvents.collect { feedback += it }
        }

        viewModel.startSession()
        viewModel.selectOption(0)
        viewModel.checkAnswer()
        viewModel.checkAnswer()
        assertEquals(listOf(true), feedback)

        viewModel.nextCard()
        viewModel.selectOption(1)
        viewModel.checkAnswer()
        viewModel.checkAnswer()

        assertEquals(listOf(true, false), feedback)
        assertTrue(viewModel.feedbackEvents.replayCache.isEmpty())
        collection.cancel()
    }

    @Test
    fun reviewAnswersPayXpIntoDailyActivityAndStoreTheLessonAndChapter() {
        seed(ReviewCard("item-mc", 1, 100))
        val viewModel = viewModel(listOf(mcExercise), exerciseLookup = { mcExercise })
        viewModel.startSession()
        viewModel.selectOption(1)

        viewModel.checkAnswer()

        val result = storedResults().single()
        assertFalse(result.correct)
        assertEquals(0, result.xpAwarded)
        assertEquals("ch-test", result.chapterId)
        assertEquals(0, storedActivities().single().xpEarned)
        assertEquals(1, storedCard("item-mc")?.box)
    }

    @Test
    fun theDueDayFollowsTheInjectedClockAndTimeZoneNotTheUtcDay() {
        // 03:59 in Toronto on Jan 6 is still Jan 5's learning day, so a card due Jan 6 is not due yet.
        seed(ReviewCard("item-mc", 1, dueEpochDay = epochDayOf("2026-01-06")))
        val beforeCutoff = viewModel(
            listOf(mcExercise),
            clock = FixedClock(Instant.parse("2026-01-06T08:59:59Z"))
        )
        assertTrue(beforeCutoff.uiState.value.dueCards.isEmpty())

        // At 04:00 the learning day is Jan 6 and the card is due.
        val afterCutoff = viewModel(
            listOf(mcExercise),
            clock = FixedClock(Instant.parse("2026-01-06T09:00:00Z"))
        )
        assertEquals(listOf("item-mc"), afterCutoff.uiState.value.dueCards.map { it.itemId })
    }

    @Test
    fun clozeIsGradedCaseSensitivelyThroughTheAnswerGrader() {
        val cloze = Exercise.Cloze(
            id = "cloze-val",
            prompt = "Declare",
            template = "____1 x = 1",
            blanks = listOf(Blank("____1", listOf("val"), caseSensitive = true)),
            explanation = ""
        )
        seed(ReviewCard("cloze-val", 1, 100))
        val viewModel = viewModel(listOf(cloze), exerciseLookup = { cloze })
        viewModel.startSession()
        viewModel.onClozeAnswerChange("____1", "VAL")

        viewModel.checkAnswer()

        assertFalse(viewModel.uiState.value.isAnswerCorrect)
    }

    @Test
    fun predictOutputUsesTheSharedNormalization() {
        val predict = Exercise.PredictOutput(
            id = "predict-sum",
            prompt = "Predict",
            code = "println(1)",
            expectedOutput = "Sum is 15",
            explanation = ""
        )
        seed(ReviewCard("predict-sum", 1, 100))
        val viewModel = viewModel(listOf(predict), exerciseLookup = { predict })
        viewModel.startSession()
        viewModel.onTextAnswerChange("Sum is 15   \n")

        viewModel.checkAnswer()

        assertTrue(viewModel.uiState.value.isAnswerCorrect)
    }

    @Test
    fun testAsyncCourseLoadingWithCourseRepository() {
        val course = SampleCourseData.defaultCourse
        val firstExercise = course.chapters.first().lessons.first().exercises.first()
        seed(ReviewCard(firstExercise.id, 1, 100))

        val recorder = progressRecorderFor(fakeRepo, listOf(course), clock = day100)
        val viewModel = ReviewViewModel(
            progressRepository = fakeRepo,
            progressRecorder = recorder,
            answerGrader = DefaultAnswerGrader(),
            courseRepository = FakeCourseRepository(listOf(course)),
            coroutineScope = testScope
        )

        assertFalse("isLoading should be false after loading completes", viewModel.uiState.value.isLoading)
        viewModel.startSession()
        assertTrue("Session should start using exercises from courseRepository", viewModel.uiState.value.isInSession)
        assertEquals(1, viewModel.uiState.value.sessionItems.size)
        assertEquals(firstExercise.id, viewModel.uiState.value.sessionItems.first().exercise.id)
        assertNull(viewModel.uiState.value.updatedBox)
    }

    private fun epochDayOf(date: String): Long =
        kotlinx.datetime.LocalDate.parse(date).toEpochDays().toLong()
}
