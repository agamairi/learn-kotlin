package dev.agam.learn.content.compiler

import dev.agam.learn.content.CourseLoader
import dev.agam.learn.model.Blank
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import dev.agam.learn.runner.LanguageId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContentCompilerTest {

    @Test
    fun testCompileFixtureCourse() {
        val resourceUrl = javaClass.classLoader.getResource("fixtures/sample-course/course.yaml")
        assertNotNull(resourceUrl, "Test fixture course.yaml must exist")
        val courseDir = File(resourceUrl.toURI()).parentFile

        val course = ContentCompiler.compileCourse(courseDir)
        assertEquals("test-kotlin", course.id)
        assertEquals(LanguageId("kotlin"), course.language)
        assertEquals(1, course.chapters.size)

        val chapter = course.chapters.first()
        assertEquals("ch01-basics", chapter.id)
        assertEquals(1, chapter.lessons.size)

        val lesson = chapter.lessons.first()
        assertEquals(5, lesson.exercises.size)

        // Validates all rules on the loaded fixture
        ContentValidator.validate(course)
    }

    @Test
    fun testCompileAllWritesOutputFile() {
        val resourceUrl = javaClass.classLoader.getResource("fixtures/sample-course/course.yaml")
        assertNotNull(resourceUrl)
        val fixturesDir = File(resourceUrl.toURI()).parentFile.parentFile

        val tempOut = File.createTempFile("course-out", "").apply {
            delete()
            mkdirs()
        }
        try {
            val compiled = ContentCompiler.compileAll(fixturesDir, tempOut)
            assertEquals(1, compiled.size)
            val outFile = File(tempOut, "test-kotlin.json")
            assertTrue(outFile.exists())

            val reloaded = CourseLoader().decodeCourse(outFile.readText())
            assertEquals("test-kotlin", reloaded.id)
            assertEquals("Kotlin Basics", reloaded.chapters.first().title)
        } finally {
            tempOut.deleteRecursively()
        }
    }

    @Test
    fun testRule1DuplicateIdFails() {
        val course = Course(
            id = "duplicate-id",
            language = LanguageId("kotlin"),
            title = "Duplicate",
            description = "Test",
            chapters = listOf(
                Chapter(
                    id = "duplicate-id", // Same as course.id
                    title = "Ch 1",
                    summary = "Summary",
                    lessons = emptyList()
                )
            )
        )

        assertFailsWith<ContentValidationException> {
            ContentValidator.validate(course)
        }
    }

    @Test
    fun testRule2CorrectIndexOutOfBoundsFails() {
        val course = Course(
            id = "c1",
            language = LanguageId("kotlin"),
            title = "Course",
            description = "Desc",
            chapters = listOf(
                Chapter(
                    id = "ch1",
                    title = "Ch 1",
                    summary = "Summary",
                    lessons = listOf(
                        Lesson(
                            id = "l1",
                            title = "L 1",
                            explanation = "Expl",
                            exercises = listOf(
                                Exercise.MultipleChoice(
                                    id = "mc1",
                                    prompt = "Prompt",
                                    options = listOf("A", "B"),
                                    correctIndex = 5, // out of bounds
                                    explanation = "Expl"
                                )
                            )
                        )
                    )
                )
            )
        )

        assertFailsWith<ContentValidationException> {
            ContentValidator.validate(course)
        }
    }

    @Test
    fun testRule3ClozePlaceholderMismatchFails() {
        val course = Course(
            id = "c1",
            language = LanguageId("kotlin"),
            title = "Course",
            description = "Desc",
            chapters = listOf(
                Chapter(
                    id = "ch1",
                    title = "Ch 1",
                    summary = "Summary",
                    lessons = listOf(
                        Lesson(
                            id = "l1",
                            title = "L 1",
                            explanation = "Expl",
                            exercises = listOf(
                                Exercise.Cloze(
                                    id = "cloze1",
                                    prompt = "Prompt",
                                    template = "val x = ____1 + ____2",
                                    blanks = listOf(
                                        Blank(placeholder = "____1", accepted = listOf("10"))
                                        // Missing ____2
                                    ),
                                    explanation = "Expl"
                                )
                            )
                        )
                    )
                )
            )
        )

        assertFailsWith<ContentValidationException> {
            ContentValidator.validate(course)
        }
    }

    @Test
    fun testRule6BlankMarkdownFails() {
        val course = Course(
            id = "c1",
            language = LanguageId("kotlin"),
            title = "Course",
            description = "Desc",
            chapters = listOf(
                Chapter(
                    id = "ch1",
                    title = "Ch 1",
                    summary = "   ", // Blank summary
                    lessons = emptyList()
                )
            )
        )

        assertFailsWith<ContentValidationException> {
            ContentValidator.validate(course)
        }
    }
}
