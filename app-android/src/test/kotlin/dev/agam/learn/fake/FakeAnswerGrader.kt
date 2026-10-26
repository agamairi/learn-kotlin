package dev.agam.learn.fake

import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.domain.CodePuzzleBlankFeedback
import dev.agam.learn.domain.CodePuzzleGradeResult
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.model.Exercise

/**
 * Deterministic local grader for multiple choice, cloze, predict output, and code puzzle exercises.
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

    override fun gradeCodePuzzle(
        puzzle: CodePuzzle,
        answers: Map<String, String>
    ): Boolean = gradeCodePuzzleWithFeedback(puzzle, answers).isCorrect

    override fun gradeCodePuzzleWithFeedback(
        puzzle: CodePuzzle,
        answers: Map<String, String>
    ): CodePuzzleGradeResult {
        if (puzzle.blanks.isEmpty()) return CodePuzzleGradeResult(isCorrect = false, feedback = emptyList())
        val feedback = puzzle.blanks.map { blank ->
            val rawAnswer = answers[blank.placeholder]
            val trimmedAnswer = rawAnswer?.trim()
            val isOffered = trimmedAnswer != null && blank.options.any { it.trim() == trimmedAnswer }
            val isCorrect = isOffered && trimmedAnswer != null && blank.accepted.any { accepted ->
                if (blank.caseSensitive) trimmedAnswer == accepted.trim()
                else trimmedAnswer.equals(accepted.trim(), ignoreCase = true)
            }
            CodePuzzleBlankFeedback(
                placeholder = blank.placeholder,
                answer = rawAnswer,
                isCorrect = isCorrect,
                message = if (isCorrect) "Correct" else "Expected one of: ${blank.accepted.joinToString()}"
            )
        }
        return CodePuzzleGradeResult(
            isCorrect = feedback.isNotEmpty() && feedback.all { it.isCorrect },
            feedback = feedback
        )
    }
}
