package dev.agam.learn.content.compiler

import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import java.io.File

/**
 * Compiles one course folder: `course.yaml` plus the chapter files it lists under `chapters/`.
 *
 * Problems are logged with a file label relative to the content folder, such as `kotlin/chapters/ch01-syntax.yaml`.
 * The course is returned only when the caller finds no errors in the log.
 */
internal class CourseCompiler(private val courseDir: File, private val log: ContentIssueLog) {

    private val courseFile = "${courseDir.name}/$COURSE_FILE"
    private val decoder = CourseDecoder(log)

    /** Reads, decodes and validates the course. Returns null when the course manifest itself cannot be decoded. */
    fun compile(): Course? {
        val root = readYaml(File(courseDir, COURSE_FILE), courseFile) ?: return null
        val manifest = root as? JsonObject ?: run {
            log.error(courseFile, ROOT, "must be a mapping of course fields")
            return null
        }

        val chapters = readChapters(manifest)
        val course = decoder.decodeCourse(courseFile, manifest)?.copy(chapters = chapters.map { it.chapter })
            ?: return null
        ContentValidator.validate(courseFile, course, chapters.map { it.file }, log)
        return course
    }

    private fun readChapters(manifest: JsonObject): List<ChapterSource> =
        when (val refs = manifest[CHAPTERS]) {
            is JsonArray -> {
                if (refs.isEmpty()) log.error(courseFile, "$.chapters", "must have at least one chapter")
                refs.mapIndexedNotNull { index, ref -> readChapter(index, ref) }
            }
            null -> {
                log.error(courseFile, "$.chapters", "must have at least one chapter")
                emptyList()
            }
            else -> {
                log.error(courseFile, "$.chapters", "must be a list of chapter file names")
                emptyList()
            }
        }

    private fun readChapter(index: Int, ref: JsonElement): ChapterSource? {
        val name = (ref as? JsonPrimitive)?.contentOrNull
        if (name.isNullOrBlank()) {
            log.error(courseFile, "$.chapters[$index]", "must be the name of a chapter file")
            return null
        }
        val fileName = if (name.endsWith(".yaml")) name else "$name.yaml"
        val chapterFile = File(courseDir, "chapters/$fileName")
        if (!chapterFile.isFile) {
            log.error(courseFile, "$.chapters[$index]", "no chapter file named chapters/$fileName")
            return null
        }

        val label = "${courseDir.name}/chapters/$fileName"
        val root = readYaml(chapterFile, label) ?: return null
        val chapter = decoder.decodeChapter(label, root) ?: return null
        return ChapterSource(label, chapter)
    }

    /** Parses a YAML file, logging a missing file or a syntax error instead of throwing. */
    private fun readYaml(file: File, label: String): JsonElement? {
        if (!file.isFile) {
            log.error(label, ROOT, "file not found")
            return null
        }
        return try {
            ContentYaml.parse(file.readText())
        } catch (error: YamlEngineException) {
            val oneLine = error.message.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            log.error(label, ROOT, "invalid YAML: $oneLine")
            null
        }
    }

    /** A decoded chapter together with the label of the file it came from. */
    private data class ChapterSource(val file: String, val chapter: Chapter)

    private companion object {
        const val ROOT = "$"
        const val COURSE_FILE = "course.yaml"
        const val CHAPTERS = "chapters"
    }
}
