package dev.agam.learn.ui.review

import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.domain.ReviewScheduler
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReviewViewModelTest {

    private lateinit var fakeRepo: FakeProgressRepository
    private lateinit var fakeScheduler: FakeReviewScheduler
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        fakeRepo = FakeProgressRepository()
        fakeScheduler = FakeReviewScheduler()
    }

    @Test
    fun testQueueLoading() {
        fakeRepo.dueCards = listOf(
            ReviewCard("card-1", 1, 100),
            ReviewCard("card-2", 2, 100)
        )
        fakeRepo.allCards = listOf(
            ReviewCard("card-1", 1, 100),
            ReviewCard("card-2", 2, 100),
            ReviewCard("card-3", 3, 110)
        )

        val viewModel = ReviewViewModel(
            progressRepository = fakeRepo,
            reviewScheduler = fakeScheduler,
            currentEpochDay = 100,
            coroutineScope = testScope
        )

        val state = viewModel.uiState.value
        assertEquals(2, state.dueCards.size)
        assertEquals(1, state.boxCounts[1])
        assertEquals(1, state.boxCounts[2])
        assertEquals(1, state.boxCounts[3])
    }

    @Test
    fun testSessionFlowCorrectAnswer() {
        fakeRepo.dueCards = listOf(ReviewCard("item-mc", 1, 100))
        fakeRepo.allCards = fakeRepo.dueCards

        val mcExercise = Exercise.MultipleChoice(
            id = "item-mc",
            prompt = "What is Kotlin?",
            options = listOf("Language", "Animal"),
            correctIndex = 0,
            explanation = "Modern language"
        )

        val viewModel = ReviewViewModel(
            progressRepository = fakeRepo,
            reviewScheduler = fakeScheduler,
            currentEpochDay = 100,
            exerciseLookup = { mcExercise },
            coroutineScope = testScope
        )

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

    private class FakeProgressRepository : ProgressRepository {
        var dueCards: List<ReviewCard> = emptyList()
        var allCards: List<ReviewCard> = emptyList()
        val upserted = mutableListOf<ReviewCard>()
        val results = mutableListOf<ExerciseResult>()

        override suspend fun recordExerciseResult(result: ExerciseResult) { results.add(result) }
        override suspend fun getExerciseResults(): List<ExerciseResult> = results
        override suspend fun getExerciseResultsForExercise(exerciseId: String): List<ExerciseResult> = emptyList()
        override suspend fun recordDailyActivity(activity: DailyActivity) {}
        override suspend fun getDailyActivities(): List<DailyActivity> = emptyList()
        override suspend fun getDailyActivity(epochDay: Long): DailyActivity? = null
        override suspend fun upsertReviewCard(card: ReviewCard) { upserted.add(card) }
        override suspend fun getAllReviewCards(): List<ReviewCard> = allCards
        override suspend fun getDueReviewCards(epochDay: Long): List<ReviewCard> = dueCards
        override suspend fun getReviewCard(itemId: String): ReviewCard? = allCards.find { it.itemId == itemId }
        override suspend fun getSetting(key: String): String? = null
        override suspend fun setSetting(key: String, value: String) {}
    }

    private class FakeReviewScheduler : ReviewScheduler {
        override fun scheduleNextReview(card: ReviewCard, correct: Boolean, currentEpochDay: Long): ReviewCard {
            val newBox = if (correct) minOf(5, card.box + 1) else 1
            val interval = when (newBox) {
                1 -> 1L
                2 -> 2L
                3 -> 4L
                4 -> 8L
                else -> 16L
            }
            return card.copy(box = newBox, dueEpochDay = currentEpochDay + interval)
        }
    }
}
