package dev.agam.learn.fake

import dev.agam.learn.model.Blank
import dev.agam.learn.model.Exercise
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAnswerGraderTest {

    private lateinit var grader: FakeAnswerGrader

    @Before
    fun setUp() {
        grader = FakeAnswerGrader()
    }

    @Test
    fun testGradeMultipleChoice() {
        val exercise = Exercise.MultipleChoice(
            id = "mc1",
            prompt = "Select val",
            options = listOf("val", "var", "let"),
            correctIndex = 0,
            explanation = "val is immutable"
        )

        assertTrue(grader.gradeMultipleChoice(exercise, 0))
        assertFalse(grader.gradeMultipleChoice(exercise, 1))
        assertFalse(grader.gradeMultipleChoice(exercise, 2))
    }

    @Test
    fun testGradeClozeCaseSensitive() {
        val exercise = Exercise.Cloze(
            id = "cl1",
            prompt = "Fill keyword",
            template = "____1 x = 5",
            blanks = listOf(
                Blank(placeholder = "____1", accepted = listOf("val"), caseSensitive = true)
            ),
            explanation = "val"
        )

        assertTrue(grader.gradeCloze(exercise, mapOf("____1" to "val")))
        assertTrue("Whitespace should be trimmed", grader.gradeCloze(exercise, mapOf("____1" to "  val  ")))
        assertFalse("Wrong casing should fail when case sensitive", grader.gradeCloze(exercise, mapOf("____1" to "VAL")))
        assertFalse(grader.gradeCloze(exercise, mapOf("____1" to "var")))
        assertFalse("Missing blank fails", grader.gradeCloze(exercise, emptyMap()))
    }

    @Test
    fun testGradeClozeCaseInsensitive() {
        val exercise = Exercise.Cloze(
            id = "cl2",
            prompt = "Fill keyword",
            template = "____1 x = 5",
            blanks = listOf(
                Blank(placeholder = "____1", accepted = listOf("val"), caseSensitive = false)
            ),
            explanation = "val"
        )

        assertTrue(grader.gradeCloze(exercise, mapOf("____1" to "VAL")))
        assertTrue(grader.gradeCloze(exercise, mapOf("____1" to "Val")))
    }

    @Test
    fun testGradePredictOutputWhitespaceAndNewlineNormalization() {
        val exercise = Exercise.PredictOutput(
            id = "po1",
            prompt = "Predict",
            code = "println(\"Hello\")",
            expectedOutput = "Hello\nWorld",
            explanation = "Two lines"
        )

        assertTrue("Exact match", grader.gradePredictOutput(exercise, "Hello\nWorld"))
        assertTrue("Windows CRLF newline normalized", grader.gradePredictOutput(exercise, "Hello\r\nWorld"))
        assertTrue("Trailing whitespace on line trimmed", grader.gradePredictOutput(exercise, "Hello  \nWorld\t"))
        assertTrue("Trailing newline trimmed", grader.gradePredictOutput(exercise, "Hello\nWorld\n\n"))
        assertFalse("Content mismatch", grader.gradePredictOutput(exercise, "Hello\nKotlin"))
    }
}
