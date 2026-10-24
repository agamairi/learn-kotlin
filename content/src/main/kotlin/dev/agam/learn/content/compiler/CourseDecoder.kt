package dev.agam.learn.content.compiler

import dev.agam.learn.content.CourseLoader
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import dev.agam.learn.model.ProblemPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Decodes the YAML trees of a course into the shared models, logging every decoding error with its file, exercise id
 * and JSON path.
 *
 * Decoding works from the inside out: each exercise is decoded on its own, then each lesson and chapter is decoded
 * as a shell with its children swapped in. A broken exercise therefore never hides the errors of its neighbours.
 */
internal class CourseDecoder(private val log: ContentIssueLog) {

    private val json = CourseLoader.DefaultJson

    /** Decodes the course fields of `course.yaml`. The returned course has no chapters; the caller adds them. */
    fun decodeCourse(file: String, manifest: JsonObject): Course? =
        decodeOrReport(file, ROOT, withEmptyList(manifest, CHAPTERS), Course.serializer())

    /** Decodes one chapter file, reporting problems against [file]. Returns null when the chapter shell is broken. */
    fun decodeChapter(file: String, root: JsonElement): Chapter? {
        val chapter = root.asMapping(file, ROOT) ?: return null
        val lessons = decodeEach(file, "$.lessons", chapter[LESSONS], "lesson") { path, element ->
            decodeLesson(file, path, element)
        }
        val shell = decodeOrReport(file, ROOT, withEmptyList(chapter, LESSONS), Chapter.serializer()) ?: return null
        return shell.copy(lessons = lessons)
    }

    private fun decodeLesson(file: String, path: String, element: JsonElement): Lesson? {
        val lesson = element.asMapping(file, path) ?: return null
        val exercises = decodeEach(file, "$path.exercises", lesson[EXERCISES], "exercise") { exercisePath, exercise ->
            decodeExercise(file, exercisePath, exercise)
        }
        val shell = decodeOrReport(file, path, withEmptyList(lesson, EXERCISES), Lesson.serializer()) ?: return null
        return shell.copy(exercises = exercises)
    }

    /** Decodes one exercise. A multi-part problem is split further so that each part reports its own path. */
    private fun decodeExercise(file: String, path: String, element: JsonElement): Exercise? {
        val exerciseId = idOf(element)
        val fields = element as? JsonObject
        if (fields == null || (fields[TYPE] as? JsonPrimitive)?.contentOrNull != MULTI_PART_PROBLEM) {
            return decodeOrReport(file, path, element, Exercise.serializer(), exerciseId)
        }

        val parts = decodeEach(file, "$path.parts", fields[PARTS], "part") { partPath, part ->
            decodeOrReport(file, partPath, part, ProblemPart.serializer(), exerciseId)
        }
        val decoded = decodeOrReport(file, path, withEmptyList(fields, PARTS), Exercise.serializer(), exerciseId)
        val shell = decoded as? Exercise.MultiPartProblem ?: return null
        return shell.copy(parts = parts)
    }

    /**
     * Decodes each item of a YAML list. A missing or empty list, or a value that is not a list, is reported here.
     * Items that fail to decode are skipped, so the rest of the list is still checked.
     */
    private fun <T : Any> decodeEach(
        file: String,
        path: String,
        element: JsonElement?,
        noun: String,
        decodeItem: (itemPath: String, item: JsonElement) -> T?
    ): List<T> = when (element) {
        is JsonArray -> {
            if (element.isEmpty()) log.error(file, path, "must have at least one $noun")
            element.mapIndexedNotNull { index, item -> decodeItem("$path[$index]", item) }
        }
        null -> {
            log.error(file, path, "must have at least one $noun")
            emptyList()
        }
        else -> {
            log.error(file, path, "must be a list of ${noun}s")
            emptyList()
        }
    }

    private fun JsonElement.asMapping(file: String, path: String): JsonObject? {
        if (this is JsonObject) return this
        log.error(file, path, "must be a mapping of fields")
        return null
    }

    private fun <T> decodeOrReport(
        file: String,
        path: String,
        element: JsonElement,
        serializer: KSerializer<T>,
        exerciseId: String? = null
    ): T? =
        try {
            json.decodeFromJsonElement(serializer, element)
        } catch (error: IllegalArgumentException) {
            // SerializationException extends IllegalArgumentException, so this also catches kotlinx decoding errors.
            val (message, relativePath) = describe(error)
            log.error(file, path + relativePath, message, exerciseId)
            null
        }

    /**
     * Splits a kotlinx error into its first line and the path it names, converted to our notation.
     * kotlinx reports paths such as `at element: $.options.0`, which becomes `.options[0]`.
     */
    private fun describe(error: Exception): Pair<String, String> {
        val firstLine = error.message.orEmpty().lineSequence().first().trim()
        val match = ELEMENT_PATH.find(firstLine) ?: return firstLine to ""
        val message = firstLine.substring(0, match.range.first)
        val relativePath = match.groupValues[1]
            .removePrefix("$")
            .replace(LIST_INDEX) { "[${it.groupValues[1]}]" }
        return message to relativePath
    }

    private fun idOf(element: JsonElement): String? =
        ((element as? JsonObject)?.get(ID) as? JsonPrimitive)?.contentOrNull

    private fun withEmptyList(source: JsonObject, field: String): JsonObject =
        JsonObject(source + (field to JsonArray(emptyList())))

    private companion object {
        const val ROOT = "$"
        const val ID = "id"
        const val TYPE = "type"
        const val CHAPTERS = "chapters"
        const val LESSONS = "lessons"
        const val EXERCISES = "exercises"
        const val PARTS = "parts"
        const val MULTI_PART_PROBLEM = "multi_part_problem"

        val ELEMENT_PATH = Regex("""\s+at (?:element|path): (\$\S*?)\.?$""")
        val LIST_INDEX = Regex("""\.(\d+)""")
    }
}
