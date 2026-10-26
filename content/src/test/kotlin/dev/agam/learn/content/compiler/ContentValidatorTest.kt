package dev.agam.learn.content.compiler

import dev.agam.learn.model.Blank
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.FaqItem
import dev.agam.learn.model.Lesson
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runner.LanguageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentValidatorTest {

    @Test
    fun wellFormedCourseHasNoIssues() {
        val issues = validate(
            chapter(
                lessons = listOf(
                    lesson(exercises = listOf(mc("mc-1", 0), mc("mc-2", 1), mc("mc-3", 2)))
                )
            )
        )

        assertEquals(emptyList(), issues)
    }

    @Test
    fun duplicateIdsNameTheFirstUse() {
        val issues = errors(
            chapter(
                lessons = listOf(
                    lesson("l01", listOf(mc("dup"))),
                    lesson("l02", listOf(mc("dup")))
                )
            )
        )

        val duplicate = issues.single()
        assertEquals("$.lessons[1].exercises[0].id", duplicate.path)
        assertEquals("dup", duplicate.exerciseId)
        assertTrue(duplicate.message.contains("already used at kotlin/chapters/ch01-one.yaml \$.lessons[0].exercises[0].id"))
    }

    @Test
    fun correctIndexOutOfRangeIsReportedWithItsPath() {
        val issues = errors(chapter(lessons = listOf(lesson(listOf(mc("mc-x", correctIndex = 5))))))

        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].correct_index", issue.path)
        assertEquals("mc-x", issue.exerciseId)
        assertEquals("kotlin/chapters/ch01-one.yaml", issue.file)
    }

    @Test
    fun optionsMustBeDistinctAndNotBlank() {
        val issues = errors(
            chapter(lessons = listOf(lesson(listOf(mc("mc-1", options = listOf("val", "val")))))),
            chapter(id = "ch02-two", lessons = listOf(lesson(listOf(mc("mc-2", options = listOf("a", " ")))))),
        )

        assertTrue(issues.any { it.path == "$.lessons[0].exercises[0].options" && it.message.contains("'val'") })
        assertTrue(issues.any { it.path == "$.lessons[0].exercises[0].options[1]" && it.message.contains("cannot be blank") })
    }

    @Test
    fun clozePlaceholderMustAppearExactlyOnce() {
        val issues = errors(
            chapter(
                lessons = listOf(
                    lesson(listOf(cloze("cloze-twice", template = "val x = ____1 + ____1", blanks = listOf(blank("____1")))))
                )
            )
        )

        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].blanks[0].placeholder", issue.path)
        assertTrue(issue.message.contains("appears 2 times"))
    }

    @Test
    fun clozeTemplatePlaceholderNeedsABlank() {
        val issues = errors(
            chapter(
                lessons = listOf(
                    lesson(listOf(cloze("cloze-missing", template = "____1 ____2", blanks = listOf(blank("____1")))))
                )
            )
        )

        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].template", issue.path)
        assertTrue(issue.message.contains("'____2' with no matching blank"))
    }

    @Test
    fun clozeBlankWithoutNumberIsRejected() {
        val issues = errors(
            chapter(
                lessons = listOf(lesson(listOf(cloze("cloze-bare", template = "sales.____ { it }", blanks = listOf(blank("____1"))))))
            )
        )

        assertTrue(issues.any { it.path.endsWith(".template") && it.message.contains("without a number") })
    }

    @Test
    fun chapterIdMustEqualItsFileName() {
        val issues = errors(chapter(id = "ch01-one", lessons = listOf(lesson(listOf(mc("mc-1"))))), files = listOf("kotlin/chapters/ch09-other.yaml"))

        val issue = issues.single()
        assertEquals("$.id", issue.path)
        assertTrue(issue.message.contains("must equal the file name 'ch09-other'"))
    }

    @Test
    fun idsMustBeLowercaseKebabCase() {
        val issues = errors(chapter(lessons = listOf(lesson(listOf(mc("MC_1"))))))

        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].id", issue.path)
        assertTrue(issue.message.contains("lowercase kebab-case"))
    }

    @Test
    fun blankMarkdownFieldsAreRejected() {
        val issues = errors(chapter(summary = "   ", lessons = listOf(lesson(listOf(mc("mc-1"))))))

        assertEquals(listOf("$.summary"), issues.map { it.path })
        assertTrue(issues.single().message.contains("summary cannot be blank"))
    }

    @Test
    fun everyMultiPartPartNeedsHiddenTestsAndReferenceSolution() {
        val problem = Exercise.MultiPartProblem(
            id = "mp-1",
            prompt = "Prompt",
            story = "Story",
            parts = listOf(part("mp-1-p1", hiddenTests = "", referenceSolution = "fun a() = 1"))
        )

        val issues = errors(chapter(lessons = listOf(lesson(listOf(problem)))))

        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].parts[0].hidden_tests", issue.path)
        assertEquals("mp-1", issue.exerciseId)
    }

    @Test
    fun hintsAreLimitedToThree() {
        val exercise = Exercise.WriteFunction(
            id = "wf-1",
            prompt = "Prompt",
            starterCode = "fun a(): Int = TODO()",
            hiddenTests = "import org.junit.Test",
            referenceSolution = "fun a(): Int = 1",
            hints = listOf("one", "two", "three", "four"),
            guided = defaultPuzzle()
        )

        val issue = errors(chapter(lessons = listOf(lesson(listOf(exercise))))).single()
        assertEquals("$.lessons[0].exercises[0].hints", issue.path)
        assertTrue(issue.message.contains("4 hints"))
    }

    @Test
    fun hiddenTestsMustNotUseKotlinTest() {
        val exercise = Exercise.WriteFunction(
            id = "wf-1",
            prompt = "Prompt",
            starterCode = "fun a(): Int = TODO()",
            hiddenTests = "import kotlin.test.assertEquals",
            referenceSolution = "fun a(): Int = 1",
            guided = defaultPuzzle()
        )

        val issue = errors(chapter(lessons = listOf(lesson(listOf(exercise))))).single()
        assertEquals("$.lessons[0].exercises[0].hidden_tests", issue.path)
        assertTrue(issue.message.contains("JUnit 4"))
    }

    @Test
    fun bundledClozeMustHaveFourOptions() {
        val badBlank = Blank(placeholder = "____1", accepted = listOf("answer"), options = listOf("answer", "other"))
        val issues = errors(
            chapter(lessons = listOf(lesson(listOf(cloze("cloze-options", "val x = ____1", listOf(badBlank))))))
        )
        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].blanks[0].options", issue.path)
        assertTrue(issue.message.contains("must declare exactly 4 options"))
    }

    @Test
    fun blankOptionsMustBeDistinct() {
        val badBlank = Blank(
            placeholder = "____1",
            accepted = listOf("a"),
            options = listOf("a", "b", "c", "b")
        )
        val issues = errors(
            chapter(lessons = listOf(lesson(listOf(cloze("cloze-distinct", "val x = ____1", listOf(badBlank))))))
        )
        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].blanks[0].options", issue.path)
        assertTrue(issue.message.contains("options must be distinct"))
    }

    @Test
    fun blankOptionsMustHaveExactlyOneAcceptedEquivalent() {
        val noMatch = Blank(
            placeholder = "____1",
            accepted = listOf("accepted"),
            options = listOf("d1", "d2", "d3", "d4")
        )
        val twoMatches = Blank(
            placeholder = "____1",
            accepted = listOf("a", "b"),
            options = listOf("a", "b", "c", "d")
        )
        val issues1 = errors(
            chapter(lessons = listOf(lesson(listOf(cloze("cloze-nomatch", "val x = ____1", listOf(noMatch))))))
        )
        assertTrue(issues1.single().message.contains("exactly one accepted equivalent, found 0"))

        val issues2 = errors(
            chapter(lessons = listOf(lesson(listOf(cloze("cloze-twomatch", "val x = ____1", listOf(twoMatches))))))
        )
        assertTrue(issues2.single().message.contains("exactly one accepted equivalent, found 2"))
    }

    @Test
    fun guidedPuzzleRequiresOneCaseSensitiveMatchingChoice() {
        val puzzle = defaultPuzzle().copy(
            blanks = defaultPuzzle().blanks.mapIndexed { index, item ->
                if (index == 0) item.copy(options = listOf("fun", "FUN", "val", "var")) else item
            }
        )
        val exercise = Exercise.WriteFunction(
            id = "wf-choice-case",
            prompt = "Prompt",
            starterCode = "fun a(): Int = TODO()",
            hiddenTests = "import org.junit.Test",
            referenceSolution = "fun a(): Int = 1",
            guided = puzzle
        )
        assertEquals(emptyList(), errors(chapter(lessons = listOf(lesson(listOf(exercise))))))

        val caseInsensitive = puzzle.copy(
            blanks = puzzle.blanks.mapIndexed { index, item ->
                if (index == 0) item.copy(caseSensitive = false) else item
            }
        )
        val issues = errors(chapter(lessons = listOf(lesson(listOf(exercise.copy(guided = caseInsensitive))))))
        assertTrue(issues.any { it.path.endsWith(".options") && it.message.contains("found 2") })
    }

    @Test
    fun guidedPuzzleMustUseThreeToFiveBlanks() {
        val tooFew = defaultPuzzle().copy(blanks = defaultPuzzle().blanks.take(2))
        val exercise = Exercise.WriteFunction(
            id = "wf-too-few-blanks",
            prompt = "Prompt",
            starterCode = "fun a(): Int = TODO()",
            hiddenTests = "import org.junit.Test",
            referenceSolution = "fun a(): Int = 1",
            guided = tooFew
        )
        val issues = errors(chapter(lessons = listOf(lesson(listOf(exercise)))))
        assertTrue(issues.any { it.path.endsWith(".blanks") && it.message.contains("3 to 5 blanks") })
    }

    @Test
    fun guidedPuzzleAcceptedChoicesMustReconstructTheReferenceExactly() {
        val wrongTemplate = CodePuzzle(
            template = "fun ____1 ____2(): Int = ____3 + 0",
            blanks = listOf(
                blank("____1", accepted = listOf("fun"), options = listOf("fun", "val", "var", "class")),
                blank("____2", accepted = listOf("a"), options = listOf("a", "b", "c", "d")),
                blank("____3", accepted = listOf("1"), options = listOf("1", "2", "3", "4"))
            )
        )
        val exercise = Exercise.WriteFunction(
            id = "wf-reconstruction",
            prompt = "Prompt",
            starterCode = "fun a(): Int = TODO()",
            hiddenTests = "import org.junit.Test",
            referenceSolution = "fun a(): Int = 1",
            guided = wrongTemplate
        )
        val issues = errors(chapter(lessons = listOf(lesson(listOf(exercise)))))
        assertTrue(issues.any { it.path.endsWith(".template") && it.message.contains("does not reconstruct reference_solution exactly") })
    }

    @Test
    fun writeFunctionMustProvideGuidedPuzzle() {
        val exercise = Exercise.WriteFunction(
            id = "wf-no-guided",
            prompt = "Prompt",
            starterCode = "fun a(): Int = TODO()",
            hiddenTests = "import org.junit.Test",
            referenceSolution = "fun a(): Int = 1",
            guided = null
        )
        val issues = errors(chapter(lessons = listOf(lesson(listOf(exercise)))))
        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].guided", issue.path)
        assertTrue(issue.message.contains("must provide guided code puzzle data"))
    }

    @Test
    fun multipartPartMustProvideGuidedPuzzle() {
        val partWithoutGuided = ProblemPart(
            id = "mp-part-no-guided",
            prompt = "Part prompt",
            hiddenTests = "class PartTest",
            referenceSolution = "fun part() = 1",
            expected = "Returns one.",
            guided = null
        )
        val problem = Exercise.MultiPartProblem(
            id = "mp-missing-guided",
            prompt = "Prompt",
            story = "Story",
            parts = listOf(partWithoutGuided)
        )
        val issues = errors(chapter(lessons = listOf(lesson(listOf(problem)))))
        val issue = issues.single()
        assertEquals("$.lessons[0].exercises[0].parts[0].guided", issue.path)
        assertTrue(issue.message.contains("must provide guided code puzzle data"))
    }

    @Test
    fun guidedPuzzleTemplateMustMatchPlaceholders() {
        val puzzle = CodePuzzle(
            template = "fun solution() = ____1 + ____2",
            blanks = listOf(blank("____1", accepted = listOf("1"), options = listOf("1", "2", "3", "4")))
        )
        val exercise = Exercise.WriteFunction(
            id = "wf-puzzle-mismatch",
            prompt = "Prompt",
            starterCode = "fun solution() = TODO()",
            hiddenTests = "import org.junit.Test",
            referenceSolution = "fun solution() = 1",
            guided = puzzle
        )
        val issues = errors(chapter(lessons = listOf(lesson(listOf(exercise)))))
        assertTrue(issues.any { it.path.endsWith(".template") && it.message.contains("'____2' with no matching blank") })
    }

    @Test
    fun predictOutputProgramMustHaveAMain() {
        val exercise = Exercise.PredictOutput(
            id = "po-1",
            prompt = "Prompt",
            code = "println(1)",
            expectedOutput = "1",
            explanation = "Prints one."
        )

        val issue = errors(chapter(lessons = listOf(lesson(listOf(exercise))))).single()
        assertEquals("$.lessons[0].exercises[0].code", issue.path)
        assertTrue(issue.message.contains("fun main()"))
    }

    @Test
    fun faqAnswersMustBeFilledIn() {
        val exercise = Exercise.MultiPartProblem(
            id = "mp-1",
            prompt = "Prompt",
            story = "Story",
            parts = listOf(part("mp-1-p1")),
            faq = listOf(FaqItem(question = "Can x be negative?", answer = ""))
        )

        val issue = errors(chapter(lessons = listOf(lesson(listOf(exercise))))).single()
        assertEquals("$.lessons[0].exercises[0].faq[0].answer", issue.path)
    }

    @Test
    fun answerSpreadOverOneIndexIsOnlyAWarning() {
        val answers = listOf(mc("mc-1", 0), mc("mc-2", 0), mc("mc-3", 0), mc("mc-4", 1), mc("mc-5", 2))

        val issues = validate(chapter(lessons = listOf(lesson(answers))))

        assertEquals(Severity.WARNING, issues.single().severity)
        assertEquals("$.lessons", issues.single().path)
        assertTrue(issues.single().message.contains("3 of 5 multiple-choice answers (60%)"))
        assertEquals(emptyList(), errors(chapter(lessons = listOf(lesson(answers)))))
    }

    @Test
    fun verifyWithCompilerServerPassesForCourseWithNoSnippets() {
        val fakeClient = object : CompilerServerClient {
            override fun version() = "2.5.0-Beta2"
            override fun run(files: List<ServerFile>) = RunResponse()
            override fun test(files: List<ServerFile>) = TestResponse()
            override fun highlight(files: List<ServerFile>) = emptyMap<String, List<Diagnostic>>()
        }
        val report = ContentValidator.verifyWithCompilerServer(
            course(chapter(lessons = listOf(lesson(listOf(mc("mc-1")))))),
            client = fakeClient
        )
        assertEquals(0, report.totalFailures)
        assertEquals(0, report.totalWarnings)
    }

    private fun validate(
        vararg chapters: Chapter,
        files: List<String> = chapters.map { "kotlin/chapters/${it.id}.yaml" }
    ): List<ContentIssue> {
        val log = ContentIssueLog()
        ContentValidator.validate("kotlin/course.yaml", course(*chapters), files, log)
        return log.issues
    }

    private fun errors(
        vararg chapters: Chapter,
        files: List<String> = chapters.map { "kotlin/chapters/${it.id}.yaml" }
    ): List<ContentIssue> = validate(*chapters, files = files).filter { it.severity == Severity.ERROR }

    private fun course(vararg chapters: Chapter) = Course(
        id = "kotlin-course",
        language = LanguageId("kotlin"),
        title = "Course",
        description = "Description",
        chapters = chapters.toList()
    )

    private fun chapter(
        id: String = "ch01-one",
        summary: String = "Summary",
        lessons: List<Lesson>
    ) = Chapter(id = id, title = "Chapter", summary = summary, lessons = lessons)

    private fun lesson(exercises: List<Exercise>) = lesson("l01", exercises)

    private fun lesson(id: String, exercises: List<Exercise>) =
        Lesson(id = id, title = "Lesson", explanation = "Explanation", exercises = exercises)

    private fun mc(id: String, correctIndex: Int = 0, options: List<String> = listOf("a", "b", "c")) =
        Exercise.MultipleChoice(
            id = id,
            prompt = "Prompt",
            options = options,
            correctIndex = correctIndex,
            explanation = "Because"
        )

    private fun cloze(id: String, template: String, blanks: List<Blank>) =
        Exercise.Cloze(id = id, prompt = "Prompt", template = template, blanks = blanks, explanation = "Because")

    private fun blank(
        placeholder: String,
        accepted: List<String> = listOf("answer"),
        options: List<String> = listOf("answer", "distractor1", "distractor2", "distractor3")
    ) = Blank(placeholder = placeholder, accepted = accepted, options = options)

    private fun defaultPuzzle(referenceSolution: String = "fun a(): Int = 1"): CodePuzzle {
        val match = Regex("fun ([A-Za-z_][A-Za-z0-9_]*)\\(\\)(: Int)? = (.+)").matchEntire(referenceSolution)
            ?: error("Test reference solution must be a one-line function: $referenceSolution")
        val name = match.groupValues[1]
        val returnType = match.groupValues[2]
        val body = match.groupValues[3]
        val signature = if (returnType.isEmpty()) "____1 ____2()" else "____1 ____2()$returnType"
        return CodePuzzle(
            template = "$signature = ____3",
            blanks = listOf(
                blank("____1", accepted = listOf("fun"), options = listOf("fun", "val", "var", "class")),
                blank("____2", accepted = listOf(name), options = listOf(name, "b", "c", "d")),
                blank("____3", accepted = listOf(body), options = listOf(body, "2", "3", "0"))
            ),
            explanation = "Returns the reference value."
        )
    }

    private fun part(
        id: String,
        hiddenTests: String = "class PartTest",
        referenceSolution: String = "fun part() = 1",
        guided: CodePuzzle? = defaultPuzzle(referenceSolution)
    ) = ProblemPart(
        id = id,
        prompt = "Part prompt",
        hiddenTests = hiddenTests,
        referenceSolution = referenceSolution,
        expected = "Returns one.",
        guided = guided
    )
}
