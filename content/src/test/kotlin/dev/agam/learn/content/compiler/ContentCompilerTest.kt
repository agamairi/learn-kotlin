package dev.agam.learn.content.compiler

import dev.agam.learn.content.CourseLoader
import dev.agam.learn.model.Exercise
import dev.agam.learn.runner.LanguageId
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContentCompilerTest {

    @Test
    fun fixtureCourseCompilesAndWritesABundleThatReloads() {
        withContent(emptyMap()) { _, outDir ->
            val result = ContentCompiler.compileAll(fixturesDir(), outDir)

            assertFalse(result.hasErrors, "fixture must compile: ${result.issues.map { it.format() }}")
            val course = result.courses.single()
            assertEquals("test-kotlin", course.id)
            assertEquals(LanguageId("kotlin"), course.language)
            assertEquals(1, course.chapters.size)
            assertEquals(5, course.chapters.first().lessons.single().exercises.size)

            val reloaded = CourseLoader().decodeCourse(File(outDir, "test-kotlin.json").readText())
            assertEquals("Kotlin Basics", reloaded.chapters.first().title)
        }
    }

    @Test
    fun quotedAndUnquotedTypedFieldsDecodeIntoTheModel() {
        withContent(
            mapOf(
                "content/kotlin/course.yaml" to COURSE_YAML,
                "content/kotlin/chapters/ch01-scalars.yaml" to """
                    id: ch01-scalars
                    title: Scalars
                    summary: Options that look like YAML values.
                    lessons:
                      - id: l01-scalars
                        title: Scalars
                        explanation: Every option is text.
                        exercises:
                          - type: multiple_choice
                            id: mc-scalars
                            prompt: Which option is zero?
                            options:
                              - "0"
                              - true
                              - null
                              - 3.5
                            correct_index: "0"
                            explanation: The first option is the text 0.
                          - type: multiple_choice
                            id: mc-bool
                            prompt: Which option is the boolean?
                            options: ["false", "true"]
                            correct_index: 1
                            explanation: The second option.
                          - type: cloze
                            id: cloze-case
                            prompt: Fill the blank.
                            template: "val ok = ____1"
                            blanks:
                              - placeholder: ____1
                                accepted: ["true"]
                                case_sensitive: "false"
                            explanation: The literal true.
                """
            )
        ) { contentDir, outDir ->
            val result = ContentCompiler.compileAll(contentDir, outDir)

            assertFalse(result.hasErrors, "unexpected errors: ${result.issues.map { it.format() }}")
            val exercises = result.courses.single().chapters.single().lessons.single().exercises
            val zeroQuestion = assertIs<Exercise.MultipleChoice>(exercises[0])
            assertEquals(listOf("0", "true", "null", "3.5"), zeroQuestion.options)
            assertEquals(0, zeroQuestion.correctIndex)
            assertEquals(1, assertIs<Exercise.MultipleChoice>(exercises[1]).correctIndex)
            val cloze = assertIs<Exercise.Cloze>(exercises[2])
            assertFalse(cloze.blanks.single().caseSensitive)
        }
    }

    @Test
    fun everyProblemIsReportedInOneRunWithFileExerciseIdAndPath() {
        withContent(
            mapOf(
                "content/kotlin/course.yaml" to """
                    id: broken-course
                    language: kotlin
                    title: Broken
                    description: Has many errors.
                    chapters:
                      - ch01-first
                      - ch02-second
                      - ch03-missing
                """,
                "content/kotlin/chapters/ch01-first.yaml" to """
                    id: ch01-renamed
                    title: First
                    summary: Summary
                    lessons:
                      - id: l01
                        title: Lesson one
                        explanation: Text
                        exercises:
                          - type: multiple_choice
                            id: mc-out-of-range
                            prompt: Pick one.
                            options: ["a", "b"]
                            correct_index: 5
                            explanation: Five is not an option.
                          - type: cloze
                            id: cloze-missing-blank
                            prompt: Fill.
                            template: "____1 + ____2"
                            blanks:
                              - placeholder: ____1
                                accepted: ["x"]
                            explanation: Needs two blanks.
                """,
                "content/kotlin/chapters/ch02-second.yaml" to """
                    id: ch02-second
                    title: Second
                    summary: Summary
                    lessons:
                      - id: l02
                        title: Lesson two
                        explanation: Text
                        exercises:
                          - type: multiple_choice
                            id: mc-out-of-range
                            prompt: Pick one.
                            options: ["a", "b"]
                            correct_index: 0
                            explanation: Fine.
                          - type: multiple_choice
                            id: mc-typo
                            prompt: Pick one.
                            optoins: ["a", "b"]
                            correct_index: 0
                            explanation: Fine.
                          - type: multi_part_problem
                            id: mp-no-tests
                            prompt: Solve it.
                            story: Story.
                            parts:
                              - id: mp-no-tests-p1
                                prompt: Part one.
                                reference_solution: "fun a() = 1"
                                expected: Returns one.
                """
            )
        ) { contentDir, outDir ->
            val result = ContentCompiler.compileAll(contentDir, outDir)

            assertTrue(result.hasErrors)
            assertTrue(result.courses.isEmpty())
            assertFalse(outDir.exists(), "no bundle may be written when there are errors")
            val errors = result.issues.filter { it.severity == Severity.ERROR }

            assertTrue(errors.any {
                it.file == "kotlin/chapters/ch01-first.yaml" && it.path == "$.id" && it.message.contains("ch01-first")
            }, "chapter id mismatch")
            assertTrue(errors.any {
                it.file == "kotlin/chapters/ch01-first.yaml" && it.exerciseId == "mc-out-of-range" &&
                    it.path == "$.lessons[0].exercises[0].correct_index"
            }, "correct_index out of range")
            assertTrue(errors.any {
                it.file == "kotlin/chapters/ch01-first.yaml" && it.exerciseId == "cloze-missing-blank" &&
                    it.path == "$.lessons[0].exercises[1].template" && it.message.contains("____2")
            }, "cloze template without blank")
            assertTrue(errors.any {
                it.file == "kotlin/chapters/ch02-second.yaml" && it.exerciseId == "mc-out-of-range" &&
                    it.path == "$.lessons[0].exercises[0].id" && it.message.contains("already used")
            }, "duplicate exercise id")
            assertTrue(errors.any {
                it.file == "kotlin/chapters/ch02-second.yaml" && it.exerciseId == "mc-typo" &&
                    it.path == "$.lessons[0].exercises[1]" && it.message.contains("'optoins'")
            }, "unknown key decode error")
            assertTrue(errors.any {
                it.file == "kotlin/chapters/ch02-second.yaml" && it.exerciseId == "mp-no-tests" &&
                    it.path == "$.lessons[0].exercises[2].parts[0]" && it.message.contains("hidden_tests")
            }, "missing hidden_tests in a multi-part part")
            assertTrue(errors.any {
                it.file == "kotlin/course.yaml" && it.path == "$.chapters[2]" && it.message.contains("ch03-missing")
            }, "missing chapter file")
        }
    }

    @Test
    fun wrongFieldTypesAreDecodeErrorsThatNameTheExercise() {
        withContent(
            mapOf(
                "content/kotlin/course.yaml" to COURSE_YAML,
                "content/kotlin/chapters/ch01-scalars.yaml" to """
                    id: ch01-scalars
                    title: Scalars
                    summary: Summary
                    lessons:
                      - id: l01
                        title: Lesson
                        explanation: Text
                        exercises:
                          - type: multiple_choice
                            id: mc-word-index
                            prompt: Pick.
                            options: ["a", "b"]
                            correct_index: first
                            explanation: A.
                          - type: cloze
                            id: cloze-bool
                            prompt: Fill.
                            template: "____1"
                            blanks:
                              - placeholder: ____1
                                accepted: ["x"]
                                case_sensitive: maybe
                            explanation: Blank.
                """
            )
        ) { contentDir, outDir ->
            val result = ContentCompiler.compileAll(contentDir, outDir)

            val errors = result.issues.filter { it.severity == Severity.ERROR }
            assertEquals(
                listOf(
                    "mc-word-index" to "$.lessons[0].exercises[0].correct_index",
                    "cloze-bool" to "$.lessons[0].exercises[1].case_sensitive"
                ),
                errors.map { it.exerciseId to it.path }
            )
        }
    }

    @Test
    fun commandLineReturnsOneForErrorsAndZeroForValidContent() {
        withContent(
            mapOf("content/kotlin/course.yaml" to COURSE_YAML.replace("ch01-scalars", "ch01-absent"))
        ) { contentDir, outDir ->
            assertEquals(1, ContentCompiler.runFromCommandLine(contentDir, outDir))
        }
        assertEquals(0, ContentCompiler.runFromCommandLine(fixturesDir(), Files.createTempDirectory("out").toFile()))
    }

    @Test
    fun answerSpreadWarningDoesNotBlockTheBundle() {
        withContent(
            mapOf(
                "content/kotlin/course.yaml" to COURSE_YAML.replace("ch01-scalars", "ch01-spread"),
                "content/kotlin/chapters/ch01-spread.yaml" to """
                    id: ch01-spread
                    title: Spread
                    summary: Summary
                    lessons:
                      - id: l01
                        title: Lesson
                        explanation: Text
                        exercises:
                          - type: multiple_choice
                            id: mc-1
                            prompt: One.
                            options: ["a", "b", "c"]
                            correct_index: 0
                            explanation: A.
                          - type: multiple_choice
                            id: mc-2
                            prompt: Two.
                            options: ["a", "b", "c"]
                            correct_index: 0
                            explanation: A.
                          - type: multiple_choice
                            id: mc-3
                            prompt: Three.
                            options: ["a", "b", "c"]
                            correct_index: 0
                            explanation: A.
                          - type: multiple_choice
                            id: mc-4
                            prompt: Four.
                            options: ["a", "b", "c"]
                            correct_index: 1
                            explanation: B.
                          - type: multiple_choice
                            id: mc-5
                            prompt: Five.
                            options: ["a", "b", "c"]
                            correct_index: 2
                            explanation: C.
                """
            )
        ) { contentDir, outDir ->
            val result = ContentCompiler.compileAll(contentDir, outDir)

            assertFalse(result.hasErrors)
            assertEquals(1, result.courses.size)
            assertTrue(File(outDir, "${result.courses.single().id}.json").exists())
            val warning = result.issues.single()
            assertEquals(Severity.WARNING, warning.severity)
            assertEquals("kotlin/chapters/ch01-spread.yaml", warning.file)
        }
    }

    @Test
    fun missingContentFolderIsAWarningNotAnError() {
        withContent(emptyMap()) { _, outDir ->
            val result = ContentCompiler.compileAll(File(outDir.parentFile, "absent"), outDir)

            assertFalse(result.hasErrors)
            assertTrue(result.courses.isEmpty())
            assertEquals(Severity.WARNING, result.issues.single().severity)
        }
    }

    private fun fixturesDir(): File {
        val resource = javaClass.classLoader.getResource("fixtures/sample-course/course.yaml")
        assertNotNull(resource, "Test fixture course.yaml must exist")
        return File(resource.toURI()).parentFile.parentFile
    }

    /** Writes [files] into a fresh temporary folder and runs [block] with the content and output folders. */
    private fun withContent(files: Map<String, String>, block: (contentDir: File, outDir: File) -> Unit) {
        val root = Files.createTempDirectory("content-compiler-test").toFile()
        try {
            files.forEach { (path, text) ->
                File(root, path).apply {
                    parentFile.mkdirs()
                    writeText(text.trimIndent())
                }
            }
            block(File(root, "content"), File(root, "out"))
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val COURSE_YAML = """
            id: tricky-course
            language: kotlin
            title: Tricky
            description: Scalars that look like YAML values.
            chapters:
              - ch01-scalars
        """
    }
}
