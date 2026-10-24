package dev.agam.learn.model

import dev.agam.learn.content.CourseLoader
import dev.agam.learn.runner.LanguageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CourseDecodingTest {

    @Test
    fun testDecodeFullCourseWithAllExerciseTypes() {
        val courseJson = """
        {
          "id": "kotlin-basics",
          "language": "kotlin",
          "title": "Kotlin Foundations",
          "description": "Learn Kotlin fundamentals with hands-on exercises.",
          "chapters": [
            {
              "id": "ch01-intro",
              "title": "Introduction to Kotlin",
              "summary": "First steps with variables and functions.",
              "lessons": [
                {
                  "id": "l01-syntax",
                  "title": "Basic Syntax",
                  "explanation": "Kotlin is concise and type-safe.",
                  "exercises": [
                    {
                      "type": "multiple_choice",
                      "id": "mc-01",
                      "prompt": "What keyword declares a read-only reference?",
                      "code": "val x = 10",
                      "options": ["val", "var", "const", "let"],
                      "correct_index": 0,
                      "explanation": "val declares an immutable reference."
                    },
                    {
                      "type": "cloze",
                      "id": "cloze-01",
                      "prompt": "Fill in the blank to print a message.",
                      "template": "____1(\"Hello, World!\")",
                      "blanks": [
                        {
                          "placeholder": "____1",
                          "accepted": ["println", "print"],
                          "case_sensitive": true
                        }
                      ],
                      "explanation": "println prints to standard output with a newline."
                    },
                    {
                      "type": "predict_output",
                      "id": "pred-01",
                      "prompt": "What does this snippet print?",
                      "code": "fun main() { println(2 + 2) }",
                      "expected_output": "4",
                      "explanation": "2 + 2 evaluates to 4."
                    },
                    {
                      "type": "write_function",
                      "id": "wf-01",
                      "prompt": "Write a function that doubles an integer.",
                      "starter_code": "fun double(n: Int): Int = TODO()",
                      "hidden_tests": "import org.junit.Test\nimport org.junit.Assert.assertEquals\nclass DoubleTest { @Test fun test() { assertEquals(4, double(2)) } }",
                      "reference_solution": "fun double(n: Int): Int = n * 2",
                      "hints": ["Multiply the input by 2"],
                      "explanation": "Expression bodies make one-liners clean."
                    },
                    {
                      "type": "multi_part_problem",
                      "id": "mp-01",
                      "prompt": "Build an in-memory ledger.",
                      "story": "You are implementing payment tracking.",
                      "parts": [
                        {
                          "id": "part-1",
                          "prompt": "Add a function to record a transaction.",
                          "starter_code": "fun record(amount: Long): Long = TODO()",
                          "hidden_tests": "class Part1Test",
                          "reference_solution": "var balance = 0L\nfun record(amount: Long): Long { balance += amount; return balance }",
                          "expected": "Returns the updated balance."
                        }
                      ],
                      "faq": [
                        {
                          "question": "Can amounts be negative?",
                          "answer": "No, amounts are non-negative."
                        }
                      ],
                      "discussion": "Notice thread safety considerations for later phases.",
                      "source_note": "Interview sample drill."
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val loader = CourseLoader()
        val course = loader.decodeCourse(courseJson)

        assertEquals("kotlin-basics", course.id)
        assertEquals(LanguageId("kotlin"), course.language)
        assertEquals("Kotlin Foundations", course.title)
        assertEquals(1, course.chapters.size)

        val chapter = course.chapters.first()
        assertEquals("ch01-intro", chapter.id)
        assertEquals(1, chapter.lessons.size)

        val lesson = chapter.lessons.first()
        assertEquals("l01-syntax", lesson.id)
        assertEquals(5, lesson.exercises.size)

        // Exercise 1: Multiple Choice
        val ex1 = lesson.exercises[0]
        assertIs<Exercise.MultipleChoice>(ex1)
        assertEquals("mc-01", ex1.id)
        assertEquals("val x = 10", ex1.code)
        assertEquals(4, ex1.options.size)
        assertEquals(0, ex1.correctIndex)
        assertEquals("val", ex1.options[ex1.correctIndex])

        // Exercise 2: Cloze
        val ex2 = lesson.exercises[1]
        assertIs<Exercise.Cloze>(ex2)
        assertEquals("cloze-01", ex2.id)
        assertEquals(1, ex2.blanks.size)
        assertEquals("____1", ex2.blanks.first().placeholder)
        assertEquals(listOf("println", "print"), ex2.blanks.first().accepted)
        assertTrue(ex2.blanks.first().caseSensitive)

        // Exercise 3: Predict Output
        val ex3 = lesson.exercises[2]
        assertIs<Exercise.PredictOutput>(ex3)
        assertEquals("pred-01", ex3.id)
        assertEquals("4", ex3.expectedOutput)

        // Exercise 4: Write Function
        val ex4 = lesson.exercises[3]
        assertIs<Exercise.WriteFunction>(ex4)
        assertEquals("wf-01", ex4.id)
        assertEquals(1, ex4.hints.size)
        assertNotNull(ex4.referenceSolution)

        // Exercise 5: Multi-Part Problem
        val ex5 = lesson.exercises[4]
        assertIs<Exercise.MultiPartProblem>(ex5)
        assertEquals("mp-01", ex5.id)
        assertEquals("You are implementing payment tracking.", ex5.story)
        assertEquals(1, ex5.parts.size)
        assertEquals("part-1", ex5.parts.first().id)
        assertEquals(1, ex5.faq.size)
        assertEquals("Can amounts be negative?", ex5.faq.first().question)
        assertEquals("Interview sample drill.", ex5.sourceNote)
    }
}
