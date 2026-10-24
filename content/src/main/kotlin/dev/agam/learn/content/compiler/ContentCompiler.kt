package dev.agam.learn.content.compiler

import dev.agam.learn.model.Course
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

/**
 * The outcome of a compile run.
 *
 * @property courses the compiled courses; empty whenever [issues] contains an error, because nothing is written then
 * @property issues every error and warning found, in discovery order
 */
data class CompileResult(val courses: List<Course>, val issues: List<ContentIssue>) {

    /** True when at least one issue is an error, which fails the build. */
    val hasErrors: Boolean
        get() = issues.any { it.severity == Severity.ERROR }
}

/**
 * Build-time compiler: reads the YAML course folders, decodes and validates them against the shared models, and writes
 * one JSON bundle per course for the Android app.
 */
object ContentCompiler {

    private val json = Json {
        prettyPrint = true
        classDiscriminator = "type"
    }

    /**
     * Compiles every course folder (a folder with `course.yaml`) under [contentDir]. All courses are checked first;
     * bundles are written to [outputDir] only when no course has an error, so the output is never half-updated.
     */
    fun compileAll(contentDir: File, outputDir: File): CompileResult {
        val log = ContentIssueLog()
        if (!contentDir.isDirectory) {
            log.warning(contentDir.path, "$", "content directory does not exist; nothing was compiled")
            return CompileResult(emptyList(), log.issues)
        }

        val courseDirs = contentDir.listFiles { file -> file.isDirectory && File(file, "course.yaml").isFile }
            .orEmpty()
            .sortedBy { it.name }
        val courses = courseDirs.mapNotNull { CourseCompiler(it, log).compile() }
        if (log.hasErrors) return CompileResult(emptyList(), log.issues)

        writeBundles(courses, outputDir)
        return CompileResult(courses, log.issues)
    }

    /**
     * Runs [compileAll] for the command line: prints every issue (errors to stderr), reports the outcome and returns
     * the process exit code, which is 1 whenever there is an error.
     */
    fun runFromCommandLine(contentDir: File, outputDir: File): Int {
        val result = compileAll(contentDir, outputDir)
        result.issues.forEach { issue ->
            if (issue.severity == Severity.ERROR) System.err.println(issue.format()) else println(issue.format())
        }
        val errors = result.issues.count { it.severity == Severity.ERROR }
        val warnings = result.issues.size - errors
        if (result.hasErrors) {
            System.err.println("Content check failed: $errors error(s), $warnings warning(s). No bundles were written.")
            return 1
        }
        result.courses.forEach { course ->
            val file = File(outputDir, "${course.id}.json")
            println("Wrote course bundle ${course.id} (${course.chapters.size} chapters) to ${file.absolutePath}")
        }
        println("Content compiled with $warnings warning(s).")
        return 0
    }

    private fun writeBundles(courses: List<Course>, outputDir: File) {
        if (courses.isEmpty()) return
        outputDir.mkdirs()
        courses.forEach { course ->
            File(outputDir, "${course.id}.json").writeText(json.encodeToString(Course.serializer(), course))
        }
    }
}

/**
 * Command-line entry point used by the `:content:compileContent` Gradle task.
 * Arguments: the content folder (default `content`) and the output folder (default `app-android/src/main/assets/courses`).
 */
fun main(args: Array<String>) {
    val contentDir = File(args.getOrElse(0) { "content" })
    val outputDir = File(args.getOrElse(1) { "app-android/src/main/assets/courses" })
    exitProcess(ContentCompiler.runFromCommandLine(contentDir, outputDir))
}
