package dev.agam.learn.content.compiler

import dev.agam.learn.content.CourseLoader
import dev.agam.learn.model.Course
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.File

/**
 * Build-time compiler that parses YAML course definitions, converts to JsonElement,
 * decodes and validates against shared models, and writes JSON bundles for the Android app.
 */
object ContentCompiler {

    private val yamlLoader = Load(LoadSettings.builder().build())
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        classDiscriminator = "type"
    }

    /**
     * Compiles all courses found in [contentDir] and writes output bundles to [outputDir].
     */
    fun compileAll(contentDir: File, outputDir: File): List<Course> {
        if (!contentDir.exists()) {
            println("Content directory $contentDir does not exist; skipping.")
            return emptyList()
        }

        outputDir.mkdirs()
        val languageDirs = contentDir.listFiles { f -> f.isDirectory && File(f, "course.yaml").exists() }
            ?: emptyArray()

        val compiledCourses = mutableListOf<Course>()
        for (langDir in languageDirs) {
            println("Compiling course from ${langDir.path}...")
            val course = compileCourse(langDir)
            ContentValidator.validate(course)

            val targetFile = File(outputDir, "${course.id}.json")
            val jsonString = json.encodeToString(Course.serializer(), course)
            targetFile.writeText(jsonString)
            println("Wrote course bundle (${course.id}) to ${targetFile.absolutePath}")
            compiledCourses.add(course)
        }
        return compiledCourses
    }

    /**
     * Compiles a single course from its directory (containing course.yaml and chapters/).
     */
    fun compileCourse(courseDir: File): Course {
        val courseYamlFile = File(courseDir, "course.yaml")
        require(courseYamlFile.exists()) { "Missing course.yaml at ${courseYamlFile.path}" }

        @Suppress("UNCHECKED_CAST")
        val courseYamlMap = yamlLoader.loadFromString(courseYamlFile.readText()) as? Map<String, Any?>
            ?: throw ContentValidationException("Invalid course.yaml root object in ${courseDir.path}")

        val chapterRefs = (courseYamlMap["chapters"] as? List<*>)?.map { it.toString() }
            ?: emptyList()

        val chaptersDir = File(courseDir, "chapters")
        require(chaptersDir.exists()) { "Missing chapters directory at ${chaptersDir.path}" }

        val chaptersList = mutableListOf<Any?>()
        for (chRef in chapterRefs) {
            val chFileName = if (chRef.endsWith(".yaml") || chRef.endsWith(".yml")) chRef else "$chRef.yaml"
            val chapterFile = File(chaptersDir, chFileName)
            require(chapterFile.exists()) { "Referenced chapter file does not exist: ${chapterFile.path}" }

            val chapterData = yamlLoader.loadFromString(chapterFile.readText())
            chaptersList.add(chapterData)
        }

        val combinedMap = courseYamlMap.toMutableMap()
        combinedMap["chapters"] = chaptersList

        val jsonElement = combinedMap.toJsonElement()
        val course = CourseLoader.DefaultJson.decodeFromJsonElement(Course.serializer(), jsonElement)
        return course
    }

    /**
     * Recursively converts SnakeYAML parsed objects into kotlinx [JsonElement].
     */
    fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
        is List<*> -> JsonArray(this.map { it.toJsonElement() })
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> JsonPrimitive(this.toString())
    }
}

fun main(args: Array<String>) {
    val inputDir = File(if (args.isNotEmpty()) args[0] else "content")
    val outputDir = File(if (args.size > 1) args[1] else "app-android/src/main/assets/courses")
    val compiled = ContentCompiler.compileAll(inputDir, outputDir)
    println("Content compilation completed successfully. Compiled ${compiled.size} courses.")
}
