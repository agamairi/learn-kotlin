package dev.agam.learn.ui.lesson

import dev.agam.learn.model.Exercise

/**
 * Stages of the lesson player flow.
 */
sealed interface LessonStep {
    data object Loading : LessonStep
    data object Completing : LessonStep
    data class SettlementFailed(val message: String) : LessonStep
    data class Explanation(val text: String) : LessonStep
    data class ExercisePlaying(
        val exerciseIndex: Int,
        val totalExercises: Int,
        val exercise: Exercise
    ) : LessonStep
    data class MistakeReviewIntro(val pendingCount: Int) : LessonStep
    data class Completed(
        val totalXp: Int,
        val correctCount: Int,
        val totalCount: Int
    ) : LessonStep
}

/**
 * UI state for AI-generated practice sheet.
 */
data class AiPracticeState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val label: String = "AI-generated, unverified",
    val cards: List<Exercise> = emptyList(),
    val currentCardIndex: Int = 0,
    val selectedOptionIndex: Int? = null,
    val clozeAnswers: Map<String, String> = emptyMap(),
    val isAnswerChecked: Boolean = false,
    val isAnswerCorrect: Boolean? = null,
    val explanation: String = "",
    val errorMessage: String? = null
) {
    val currentCard: Exercise?
        get() = cards.getOrNull(currentCardIndex)

    val canCheckAnswer: Boolean
        get() {
            if (isAnswerChecked) return false
            return when (val card = currentCard) {
                is Exercise.MultipleChoice -> selectedOptionIndex != null
                is Exercise.Cloze -> {
                    card.blanks.all { blank ->
                        val ans = clozeAnswers[blank.placeholder]?.trim()
                        !ans.isNullOrEmpty()
                    }
                }
                else -> false
            }
        }
}

/**
 * Payload for an in-flight 5-in-a-row (or 10, 15, ...) milestone celebration.
 */
data class MilestoneCelebration(
    val id: Long,
    val streakCount: Int
)

/**
 * One-shot feedback event to play sound/haptic cues exactly once without recomposition replays.
 */
data class LessonFeedbackEvent(
    val id: Long,
    val correct: Boolean,
    val isMilestone: Boolean,
    val streak: Int
)

/**
 * UI state for the lesson player.
 */
data class LessonUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val courseId: String = "",
    val lessonId: String = "",
    val lessonTitle: String = "",
    val chapterTitle: String = "",
    val step: LessonStep = LessonStep.Loading,
    val selectedOptionIndex: Int? = null,
    val clozeAnswers: Map<String, String> = emptyMap(),
    val predictOutputText: String = "",
    val isAnswerChecked: Boolean = false,
    val isAnswerCorrect: Boolean? = null,
    val currentExplanation: String = "",
    val xpEarnedInLesson: Int = 0,
    val correctExercisesCount: Int = 0,
    val totalExercisesCount: Int = 0,
    /** Number of unique missed lesson cards still waiting in the end-of-lesson review. */
    val pendingMistakeCount: Int = 0,
    /** True while the learner is working through the separate mistake-review round. */
    val isMistakeReview: Boolean = false,
    val isAiAvailable: Boolean = false,
    val isInterviewMode: Boolean = false,
    val hasApiKey: Boolean = false,
    val aiPracticeState: AiPracticeState = AiPracticeState(),
    val consecutiveCorrectStreak: Int = 0,
    val activeMilestone: MilestoneCelebration? = null,
    val feedbackEvent: LessonFeedbackEvent? = null
) {
    val consecutiveStreak: Int
        get() = consecutiveCorrectStreak
    /** True when the current exercise is completed in a studio screen instead of on this screen. */
    val isStudioExercise: Boolean
        get() {
            val exercise = (step as? LessonStep.ExercisePlaying)?.exercise ?: return false
            return exercise is Exercise.WriteFunction || exercise is Exercise.MultiPartProblem
        }

    val canCheckAnswer: Boolean
        get() {
            if (isAnswerChecked) return false
            val currentExerciseStep = step as? LessonStep.ExercisePlaying ?: return false
            return when (val exercise = currentExerciseStep.exercise) {
                is Exercise.MultipleChoice -> selectedOptionIndex != null
                is Exercise.Cloze -> {
                    exercise.blanks.all { blank ->
                        val ans = clozeAnswers[blank.placeholder]?.trim()
                        !ans.isNullOrEmpty()
                    }
                }
                is Exercise.PredictOutput -> predictOutputText.isNotBlank()
                // Studio exercises are completed in their own screen, never checked here.
                is Exercise.WriteFunction, is Exercise.MultiPartProblem -> false
            }
        }

    val progressFraction: Float
        get() {
            if (step is LessonStep.Completed) return 1f
            if (step is LessonStep.MistakeReviewIntro || isMistakeReview) {
                if (totalExercisesCount == 0) return 0f
                return (correctExercisesCount.toFloat() / totalExercisesCount).coerceIn(0f, 1f)
            }
            val playingStep = step as? LessonStep.ExercisePlaying ?: return 0f
            if (playingStep.totalExercises == 0) return 0f
            return (playingStep.exerciseIndex.toFloat() / playingStep.totalExercises)
        }
}
