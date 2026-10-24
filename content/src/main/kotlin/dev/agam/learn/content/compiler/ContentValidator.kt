package dev.agam.learn.content.compiler

import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import java.io.File
import kotlin.math.roundToInt

/**
 * Checks a decoded course against docs/CONTENT_SCHEMA.md.
 *
 * Every problem goes into the [ContentIssueLog] with its file, exercise id and JSON path. Nothing is thrown, so one run
 * reports all problems. Hard failures are errors; the answer-spread rule is a warning.
 *
 * Lists that must not be empty (chapters, lessons, exercises, parts) are checked by [CourseDecoder] instead. There a
 * missing list can be told apart from a list whose items all failed to decode, so no misleading follow-up error appears.
 */
object ContentValidator {

    private val KEBAB_CASE = Regex("[a-z0-9]+(-[a-z0-9]+)*")

    /** The schema asks that no more than about 40% of a chapter's multiple-choice answers sit at one index. */
    private const val MAX_SHARE_OF_ONE_INDEX = 0.4

    /**
     * Validates [course]. [courseFile] names the file that holds the course fields, and [chapterFiles] names the source
     * file of each chapter, in the order of [Course.chapters].
     */
    fun validate(courseFile: String, course: Course, chapterFiles: List<String>, log: ContentIssueLog) {
        require(chapterFiles.size == course.chapters.size) { "Expected one chapter file per chapter" }

        val ids = UniqueIds(log)
        val courseSite = ContentSite(log, courseFile)
        ids.register(courseFile, "$.id", course.id, exerciseId = null)
        courseSite.requireText("$.title", course.title, "title")
        courseSite.requireText("$.description", course.description, "description")
        courseSite.requireText("$.language", course.language.value, "language")

        course.chapters.zip(chapterFiles).forEach { (chapter, file) -> validateChapter(log, ids, file, chapter) }
    }

    /**
     * Stub for the rules that need a running kotlin-compiler-server: predict_output programs must print exactly their
     * expected_output, and every reference_solution must pass its hidden_tests (CONTENT_SCHEMA rules 4 and 5).
     *
     * Not implemented yet. It waits for docs/spike-report.md, which fixes the server endpoints and limits.
     *
     * @throws NotImplementedError always, so the stub can never be mistaken for a passed check
     */
    fun verifyWithCompilerServer(course: Course) {
        throw NotImplementedError(
            "verifyWithCompilerServer is not implemented yet; it waits for docs/spike-report.md (course ${course.id})"
        )
    }

    private fun validateChapter(log: ContentIssueLog, ids: UniqueIds, file: String, chapter: Chapter) {
        val site = ContentSite(log, file)
        val expectedId = File(file).nameWithoutExtension
        if (chapter.id != expectedId) {
            site.reportError("$.id", "chapter id '${chapter.id}' must equal the file name '$expectedId'")
        }
        ids.register(file, "$.id", chapter.id, exerciseId = null)
        site.requireText("$.title", chapter.title, "title")
        site.requireText("$.summary", chapter.summary, "summary")

        chapter.lessons.forEachIndexed { index, lesson -> validateLesson(log, ids, file, "$.lessons[$index]", lesson) }
        checkAnswerSpread(site, chapter)
    }

    private fun validateLesson(log: ContentIssueLog, ids: UniqueIds, file: String, path: String, lesson: Lesson) {
        val site = ContentSite(log, file)
        ids.register(file, "$path.id", lesson.id, exerciseId = null)
        site.requireText("$path.title", lesson.title, "title")
        site.requireText("$path.explanation", lesson.explanation, "explanation")
        lesson.exercises.forEachIndexed { index, exercise ->
            validateExercise(log, ids, file, "$path.exercises[$index]", exercise)
        }
    }

    private fun validateExercise(log: ContentIssueLog, ids: UniqueIds, file: String, path: String, exercise: Exercise) {
        ids.register(file, "$path.id", exercise.id, exerciseId = exercise.id)
        if (exercise is Exercise.MultiPartProblem) {
            exercise.parts.forEachIndexed { index, part ->
                ids.register(file, "$path.parts[$index].id", part.id, exerciseId = exercise.id)
            }
        }
        ExerciseValidator.validate(ContentSite(log, file, exercise.id), path, exercise)
    }

    /** Warns when more than [MAX_SHARE_OF_ONE_INDEX] of a chapter's multiple-choice answers share one index. */
    private fun checkAnswerSpread(site: ContentSite, chapter: Chapter) {
        val answers = chapter.lessons
            .flatMap { it.exercises }
            .filterIsInstance<Exercise.MultipleChoice>()
            .map { it.correctIndex }
        if (answers.isEmpty()) return

        val (index, count) = answers.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return
        val share = count.toDouble() / answers.size
        if (share > MAX_SHARE_OF_ONE_INDEX) {
            val percent = (share * 100).roundToInt()
            val limit = (MAX_SHARE_OF_ONE_INDEX * 100).roundToInt()
            site.reportWarning(
                "$.lessons",
                "$count of ${answers.size} multiple-choice answers ($percent%) have correct_index $index; " +
                    "keep any one index at or below $limit%"
            )
        }
    }

    /** Ids must be lowercase kebab-case and unique across the whole course; each duplicate names the first use. */
    private class UniqueIds(private val log: ContentIssueLog) {
        private val firstUse = mutableMapOf<String, String>()

        fun register(file: String, path: String, id: String, exerciseId: String?) {
            when {
                id.isBlank() -> log.error(file, path, "id cannot be blank", exerciseId)
                !KEBAB_CASE.matches(id) -> log.error(file, path, "id '$id' must be lowercase kebab-case", exerciseId)
            }
            val previous = firstUse.putIfAbsent(id, "$file $path")
            if (previous != null) {
                log.error(file, path, "id '$id' is already used at $previous", exerciseId)
            }
        }
    }
}
