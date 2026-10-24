package dev.agam.learn.content.compiler

import dev.agam.learn.model.Blank
import dev.agam.learn.model.Chapter
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
            hints = listOf("one", "two", "three", "four")
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
            referenceSolution = "fun a(): Int = 1"
        )

        val issue = errors(chapter(lessons = listOf(lesson(listOf(exercise))))).single()
        assertEquals("$.lessons[0].exercises[0].hidden_tests", issue.path)
        assertTrue(issue.message.contains("JUnit 4"))
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
    fun verifyWithCompilerServerIsAStubThatNeverPasses() {
        assertFailsWith<NotImplementedError> {
            ContentValidator.verifyWithCompilerServer(course(chapter(lessons = listOf(lesson(listOf(mc("mc-1")))))))
        }
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

    private fun blank(placeholder: String) = Blank(placeholder = placeholder, accepted = listOf("answer"))

    private fun part(
        id: String,
        hiddenTests: String = "class PartTest",
        referenceSolution: String = "fun part() = 1"
    ) = ProblemPart(
        id = id,
        prompt = "Part prompt",
        hiddenTests = hiddenTests,
        referenceSolution = referenceSolution,
        expected = "Returns one."
    )
}
