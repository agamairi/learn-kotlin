package dev.agam.learn.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ExerciseAttempt
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.data.RecordOutcome
import dev.agam.learn.di.AppContainer
import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.domain.DefaultAnswerGrader
import dev.agam.learn.model.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the Leitner review queue: card loading, answer grading, and each answer's
 * write. Answers go through [ProgressRecorder], which reschedules the card and pays the XP.
 *
 * "Today" is the recorder's effective learning day, so the queue and the scheduler always agree.
 */
class ReviewViewModel(
    private val progressRepository: ProgressRepository,
    private val progressRecorder: ProgressRecorder,
    private val answerGrader: AnswerGrader = DefaultAnswerGrader(),
    private val courseRepository: CourseRepository? = null,
    private val exerciseLookup: ((itemId: String) -> Exercise?)? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private fun today(): Long = progressRecorder.today()

    private val scope = coroutineScope ?: viewModelScope
    private var cachedExercises: Map<String, Exercise>? = null

    private val _uiState = MutableStateFlow(ReviewUiState(isLoading = true))
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            if (courseRepository != null && cachedExercises == null) {
                cachedExercises = courseRepository.listCourses()
                    .flatMap { it.chapters }
                    .flatMap { it.lessons }
                    .flatMap { it.exercises }
                    .associateBy { it.id }
            }
            loadQueueInternal()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadQueue() {
        scope.launch {
            loadQueueInternal()
        }
    }

    private suspend fun loadQueueInternal() {
        val due = progressRepository.getDueReviewCards(today())
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

    fun startSession() {
        val dueCards = _uiState.value.dueCards
        if (dueCards.isEmpty()) return

        // Cards without a gradable exercise are left out of the session rather than replaced with a
        // placeholder: a placeholder would let any answer pass.
        val sessionItems = dueCards.mapNotNull { card ->
            val exercise = (exerciseLookup?.invoke(card.itemId) ?: cachedExercises?.get(card.itemId))
                ?.takeIf { it.isSelfGradable() } ?: return@mapNotNull null
            ReviewSessionItem(card = card, exercise = exercise)
        }
        if (sessionItems.isEmpty()) return

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

    /**
     * Grades the current answer and records it. A second call for the same card is ignored, so a
     * double tap records the answer once.
     */
    fun checkAnswer() {
        val currentState = _uiState.value
        if (currentState.isAnswerChecked) return
        val currentItem = currentState.sessionItems.getOrNull(currentState.currentCardIndex) ?: return

        val isCorrect = when (val ex = currentItem.exercise) {
            is Exercise.MultipleChoice -> answerGrader.gradeMultipleChoice(ex, currentState.selectedOptionIndex ?: -1)
            is Exercise.Cloze -> answerGrader.gradeCloze(ex, currentState.clozeAnswers)
            is Exercise.PredictOutput -> answerGrader.gradePredictOutput(ex, currentState.textAnswer)
            else -> false
        }

        // The answer is marked checked before recording, so a second tap cannot record it again.
        _uiState.update {
            it.copy(
                isAnswerChecked = true,
                isAnswerCorrect = isCorrect,
                totalAnswered = it.totalAnswered + 1,
                totalCorrect = it.totalCorrect + if (isCorrect) 1 else 0
            )
        }

        scope.launch {
            val outcome = progressRecorder.record(
                ExerciseAttempt(
                    attemptId = "review:${currentItem.card.itemId}:${today()}",
                    exerciseId = currentItem.card.itemId,
                    correct = isCorrect,
                    review = true
                )
            )
            if (outcome is RecordOutcome.Recorded) {
                val updatedCard = outcome.reviewCard
                _uiState.update {
                    it.copy(
                        updatedBox = updatedCard?.box,
                        nextReviewDays = updatedCard?.let { card ->
                            (card.dueEpochDay - today()).toInt().coerceAtLeast(1)
                        },
                        xpEarned = it.xpEarned + outcome.xpAwarded
                    )
                }
            }
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

    /** Review grades answers locally, so only the card types that can be graded here are reviewed. */
    private fun Exercise.isSelfGradable(): Boolean =
        this is Exercise.MultipleChoice || this is Exercise.Cloze || this is Exercise.PredictOutput

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ReviewViewModel(
                        progressRepository = appContainer.progressRepository,
                        progressRecorder = appContainer.progressRecorder,
                        answerGrader = appContainer.answerGrader,
                        courseRepository = appContainer.courseRepository
                    ) as T
                }
            }
    }
}
