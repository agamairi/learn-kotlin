package dev.agam.learn.ui.lesson

import dev.agam.learn.model.Exercise

/**
 * Stages of the lesson player flow.
 */
sealed interface LessonStep {
    data object Loading : LessonStep
    data class Explanation(val text: String) : LessonStep
    data class ExercisePlaying(
        val exerciseIndex: Int,
        val totalExercises: Int,
        val exercise: Exercise
    ) : LessonStep
    data class Completed(
        val totalXp: Int,
        val correctCount: Int,
        val totalCount: Int
    ) : LessonStep
}

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
    val totalExercisesCount: Int = 0
) {
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
            val playingStep = step as? LessonStep.ExercisePlaying ?: return if (step is LessonStep.Completed) 1f else 0f
            if (playingStep.totalExercises == 0) return 0f
            return (playingStep.exerciseIndex.toFloat() / playingStep.totalExercises)
        }
}
