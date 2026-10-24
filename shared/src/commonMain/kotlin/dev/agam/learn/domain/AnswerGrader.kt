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

/** Per-blank result returned by the detailed cloze grader. */
data class ClozeBlankFeedback(
    val placeholder: String,
    val answer: String?,
    val isCorrect: Boolean,
    val message: String
)

/** Complete cloze result, including feedback in exercise blank order. */
data class ClozeGradeResult(
    val isCorrect: Boolean,
    val feedback: List<ClozeBlankFeedback>
) {
    val allCorrect: Boolean get() = isCorrect
}

/**
 * Deterministic graders for multiple-choice, cloze, and predict-output cards.
 */
class DefaultAnswerGrader : AnswerGrader {
    private val multipleChoice = MultipleChoiceGrader()
    private val cloze = ClozeGrader()
    private val predictOutput = PredictOutputGrader()

    override fun gradeMultipleChoice(
        exercise: Exercise.MultipleChoice,
        selectedIndex: Int
    ): Boolean = multipleChoice.grade(exercise, selectedIndex)

    override fun gradeCloze(
        exercise: Exercise.Cloze,
        answers: Map<String, String>
    ): Boolean = cloze.grade(exercise, answers)

    override fun gradePredictOutput(
        exercise: Exercise.PredictOutput,
        userOutput: String
    ): Boolean = predictOutput.grade(exercise, userOutput)

    /** Returns cloze correctness and feedback for every expected blank. */
    fun gradeClozeWithFeedback(
        exercise: Exercise.Cloze,
        answers: Map<String, String>
    ): ClozeGradeResult = cloze.gradeWithFeedback(exercise, answers)
}

/** Grades a multiple-choice answer by its option index. */
class MultipleChoiceGrader {
    fun grade(exercise: Exercise.MultipleChoice, selectedIndex: Int): Boolean =
        selectedIndex >= 0 && selectedIndex == exercise.correctIndex && selectedIndex < exercise.options.size
}

/** Grades each cloze blank independently, including case and accepted alternatives. */
class ClozeGrader {
    fun grade(exercise: Exercise.Cloze, answers: Map<String, String>): Boolean =
        gradeWithFeedback(exercise, answers).isCorrect

    /** Returns one feedback item for every blank, in the model's declared order. */
    fun gradeWithFeedback(
        exercise: Exercise.Cloze,
        answers: Map<String, String>
    ): ClozeGradeResult {
        val feedback = exercise.blanks.map { blank ->
            val answer = answers[blank.placeholder]
            val correct = answer != null && blank.accepted.any { accepted ->
                answer.trim().matchesAccepted(accepted.trim(), blank.caseSensitive)
            }
            ClozeBlankFeedback(
                placeholder = blank.placeholder,
                answer = answer,
                isCorrect = correct,
                message = if (correct) "Correct" else "Expected one of: ${blank.accepted.joinToString()}")
        }
        return ClozeGradeResult(
            isCorrect = feedback.all { it.isCorrect },
            feedback = feedback
        )
    }
}

/** Grades predicted output while preserving all non-trailing whitespace exactly. */
class PredictOutputGrader {
    fun grade(exercise: Exercise.PredictOutput, userOutput: String): Boolean =
        normalize(exercise.expectedOutput) == normalize(userOutput)

    private fun normalize(output: String): String = output
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .joinToString("\n") { line -> line.trimEnd(' ', '\t') }
        .trimEnd('\n')
}

/** Compatibility name for callers that use the conventional implementation suffix. */
typealias AnswerGraderImpl = DefaultAnswerGrader

private fun String.matchesAccepted(accepted: String, caseSensitive: Boolean): Boolean =
    if (caseSensitive) this == accepted else equals(accepted, ignoreCase = true)
