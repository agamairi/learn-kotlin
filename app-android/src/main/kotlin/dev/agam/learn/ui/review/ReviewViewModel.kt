package dev.agam.learn.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.domain.ReviewScheduler
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing Leitner spaced repetition review queue, card loading,
 * answer grading, and Leitner schedule updating.
 */
class ReviewViewModel(
    private val progressRepository: ProgressRepository,
    private val reviewScheduler: ReviewScheduler,
    private val currentEpochDay: Long = System.currentTimeMillis() / (1000 * 60 * 60 * 24),
    private val exerciseLookup: (itemId: String) -> Exercise? = { null },
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        loadQueue()
    }

    fun loadQueue() {
        scope.launch {
            val due = progressRepository.getDueReviewCards(currentEpochDay)
            val all = progressRepository.getAllReviewCards()

            val boxCounts = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0)
            all.forEach { card ->
                val box = card.box.coerceIn(1, 5)
                boxCounts[box] = (boxCounts[box] ?: 0) + 1
            }

            _uiState.update {
                it.copy(
                    dueCards = due,
                    boxCounts = boxCounts,
                    isInSession = false,
                    isSessionComplete = false
                )
            }
        }
    }

    fun startSession() {
        val dueCards = _uiState.value.dueCards
        if (dueCards.isEmpty()) return

        val sessionItems = dueCards.map { card ->
            val exercise = exerciseLookup(card.itemId) ?: fallbackExercise(card.itemId)
            ReviewSessionItem(card = card, exercise = exercise)
        }

        _uiState.update {
            it.copy(
                isInSession = true,
                isSessionComplete = false,
                sessionItems = sessionItems,
                currentCardIndex = 0,
                selectedOptionIndex = null,
                clozeAnswers = emptyMap(),
                textAnswer = "",
                isAnswerChecked = false,
                totalAnswered = 0,
                totalCorrect = 0,
                xpEarned = 0
            )
        }
    }

    fun selectOption(index: Int) {
        _uiState.update { it.copy(selectedOptionIndex = index) }
    }

    fun onClozeAnswerChange(placeholder: String, value: String) {
        _uiState.update { current ->
            val updated = current.clozeAnswers.toMutableMap()
            updated[placeholder] = value
            current.copy(clozeAnswers = updated)
        }
    }

    fun onTextAnswerChange(text: String) {
        _uiState.update { it.copy(textAnswer = text) }
    }

    fun checkAnswer() {
        val currentState = _uiState.value
        val currentItem = currentState.sessionItems.getOrNull(currentState.currentCardIndex) ?: return

        val isCorrect = when (val ex = currentItem.exercise) {
            is Exercise.MultipleChoice -> {
                currentState.selectedOptionIndex == ex.correctIndex
            }
            is Exercise.Cloze -> {
                ex.blanks.all { blank ->
                    val userAns = currentState.clozeAnswers[blank.placeholder]?.trim().orEmpty()
                    blank.accepted.any {
                        if (blank.caseSensitive) it == userAns else it.equals(userAns, ignoreCase = true)
                    }
                }
            }
            is Exercise.PredictOutput -> {
                currentState.textAnswer.trim() == ex.expectedOutput.trim()
            }
            else -> true
        }

        val updatedCard = reviewScheduler.scheduleNextReview(
            card = currentItem.card,
            correct = isCorrect,
            currentEpochDay = currentEpochDay
        )

        val xp = if (isCorrect) 10 else 0

        scope.launch {
            progressRepository.upsertReviewCard(updatedCard)
            progressRepository.recordExerciseResult(
                ExerciseResult(
                    exerciseId = currentItem.card.itemId,
                    correct = isCorrect,
                    xpAwarded = xp,
                    epochMillis = System.currentTimeMillis()
                )
            )
        }

        _uiState.update {
            it.copy(
                isAnswerChecked = true,
                isAnswerCorrect = isCorrect,
                updatedBox = updatedCard.box,
                nextReviewDays = (updatedCard.dueEpochDay - currentEpochDay).toInt().coerceAtLeast(1),
                totalAnswered = it.totalAnswered + 1,
                totalCorrect = it.totalCorrect + if (isCorrect) 1 else 0,
                xpEarned = it.xpEarned + xp
            )
        }
    }

    fun nextCard() {
        val currentState = _uiState.value
        val nextIndex = currentState.currentCardIndex + 1

        if (nextIndex >= currentState.sessionItems.size) {
            _uiState.update {
                it.copy(
                    isSessionComplete = true,
                    isInSession = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    currentCardIndex = nextIndex,
                    selectedOptionIndex = null,
                    clozeAnswers = emptyMap(),
                    textAnswer = "",
                    isAnswerChecked = false,
                    isAnswerCorrect = false,
                    updatedBox = null,
                    nextReviewDays = null
                )
            }
        }
    }

    fun exitSession() {
        _uiState.update {
            it.copy(
                isInSession = false,
                isSessionComplete = false
            )
        }
        loadQueue()
    }

    private fun fallbackExercise(itemId: String): Exercise {
        return Exercise.MultipleChoice(
            id = itemId,
            prompt = "Review flashcard for item: $itemId",
            options = listOf("Correct", "Incorrect"),
            correctIndex = 0,
            explanation = "Item $itemId review card."
        )
    }
}
