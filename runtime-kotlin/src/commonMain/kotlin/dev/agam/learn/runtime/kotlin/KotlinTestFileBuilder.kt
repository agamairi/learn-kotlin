package dev.agam.learn.runtime.kotlin

import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runner.SourceFile

/**
 * Builds the fixed-name source files expected by kotlin-compiler-server.
 *
 * Hidden tests must contain one top-level JUnit 4 test class in the default package. Keeping the
 * generated names fixed avoids passing content-controlled paths to the server.
 */
object KotlinTestFileBuilder {
    private val classPattern = Regex("(?m)^\\s*(?:(?:public|internal|private|protected)\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val packagePattern = Regex("(?m)^\\s*package\\s+")
    private val testMethodPattern = Regex(
        "(?s)@[A-Za-z0-9_.]*Test\\b(?:\\s*\\([^)]*\\))?\\s*(?:@[A-Za-z0-9_.]+\\s*)*(?:suspend\\s+)?fun\\s+([A-Za-z_][A-Za-z0-9_]*)"
    )

    /**
     * Returns `Solution.kt` followed by the hidden test file.
     *
     * @throws IllegalArgumentException if the hidden source does not meet the server's test-file
     * rules or does not contain exactly one top-level test class.
     */
    fun build(learnerCode: String, hiddenTests: String): List<SourceFile> = listOf(
        SourceFile("Solution.kt", learnerCode),
        buildTestFile(hiddenTests)
    )

    /** Builds files for a write-function exercise. */
    fun build(learnerCode: String, exercise: Exercise.WriteFunction): List<SourceFile> =
        build(learnerCode, exercise.hiddenTests)

    /** Builds files for one multi-part problem part. */
    fun build(learnerCode: String, part: ProblemPart): List<SourceFile> =
        build(learnerCode, part.hiddenTests)

    /** Converts hidden tests into a server-compatible source file. */
    fun buildTestFile(hiddenTests: String): SourceFile {
        require(!packagePattern.containsMatchIn(hiddenTests)) {
            "Hidden test classes must be in the default package."
        }
        val classes = classPattern.findAll(hiddenTests).map { it.groupValues[1] }.toList()
        require(classes.size == 1) {
            "Hidden tests must contain exactly one top-level test class."
        }
        return SourceFile("${classes.single()}.kt", hiddenTests)
    }

    internal fun testMethodNames(testFile: SourceFile): List<String> = testMethodNames(testFile.text)

    internal fun testMethodNames(testText: String): List<String> =
        testMethodPattern.findAll(testText).map { it.groupValues[1] }.toList()
}
