package dev.agam.learn.domain

import dev.agam.learn.model.Exercise

/**
 * Local deterministic grading engine for non-code exercises.
 */
interface AnswerGrader {
    /**
     * Grades a multiple choice selection.
     */
    fun gradeMultipleChoice(
        exercise: Exercise.MultipleChoice,
        selectedIndex: Int
    ): Boolean

    /**
     * Grades cloze blank inputs keyed by placeholder (e.g. "____1" -> "val").
     */
    fun gradeCloze(
        exercise: Exercise.Cloze,
        answers: Map<String, String>
    ): Boolean

    /**
     * Grades predicted output, comparing against expected terminal stdout.
     */
    fun gradePredictOutput(
        exercise: Exercise.PredictOutput,
        userOutput: String
    ): Boolean
}
