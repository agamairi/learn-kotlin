package dev.agam.learn.fake

import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.model.Exercise

/**
 * Deterministic local grader for multiple choice, cloze, and predict output exercises.
 */
class FakeAnswerGrader : AnswerGrader {

    override fun gradeMultipleChoice(
        exercise: Exercise.MultipleChoice,
        selectedIndex: Int
    ): Boolean {
        return selectedIndex == exercise.correctIndex
    }

    override fun gradeCloze(
        exercise: Exercise.Cloze,
        answers: Map<String, String>
    ): Boolean {
        if (exercise.blanks.isEmpty()) return true
        for (blank in exercise.blanks) {
            val userAnswer = answers[blank.placeholder]?.trim() ?: ""
            if (userAnswer.isEmpty()) return false
            val matched = if (blank.caseSensitive) {
                blank.accepted.any { it.trim() == userAnswer }
            } else {
                blank.accepted.any { it.trim().equals(userAnswer, ignoreCase = true) }
            }
            if (!matched) return false
        }
        return true
    }

    override fun gradePredictOutput(
        exercise: Exercise.PredictOutput,
        userOutput: String
    ): Boolean {
        fun normalize(text: String): String {
            return text.replace("\r\n", "\n")
                .lines()
                .map { it.trimEnd() }
                .joinToString("\n")
                .trimEnd()
        }
        return normalize(userOutput) == normalize(exercise.expectedOutput)
    }
}
