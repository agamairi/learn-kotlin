package dev.agam.learn.content.compiler

import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import java.io.File
import kotlin.system.exitProcess

/**
 * One issue detected during content verification against the compiler server.
 */
data class VerificationFinding(
    val severity: Severity,
    val chapterFile: String,
    val exerciseId: String,
    val partId: String? = null,
    val check: String,
    val evidence: String
)

/**
 * Verification metrics for one exercise type.
 */
data class ExerciseTypeSummary(
    val exerciseType: String,
    val checked: Int,
    val passed: Int,
    val failed: Int,
    val warnings: Int
)

/**
 * Overall outcome of content verification.
 */
data class VerificationReport(
    val serverVersion: String,
    val summaries: List<ExerciseTypeSummary>,
    val findings: List<VerificationFinding>
) {
    val totalFailures: Int get() = findings.count { it.severity == Severity.ERROR }
    val totalWarnings: Int get() = findings.count { it.severity == Severity.WARNING }
    val hasErrors: Boolean get() = totalFailures > 0

    fun renderSummaryTable(): String {
        val sb = StringBuilder()
        sb.appendLine("| Exercise Type | Checked | Passed | Failed | Warnings |")
        sb.appendLine("| --- | --- | --- | --- | --- |")
        for (s in summaries) {
            sb.appendLine("| ${s.exerciseType} | ${s.checked} | ${s.passed} | ${s.failed} | ${s.warnings} |")
        }
        val totalChecked = summaries.sumOf { it.checked }
        val totalPassed = summaries.sumOf { it.passed }
        val totalFailed = summaries.sumOf { it.failed }
        val totalWarnings = summaries.sumOf { it.warnings }
        sb.appendLine("| **Total** | **$totalChecked** | **$totalPassed** | **$totalFailed** | **$totalWarnings** |")
        return sb.toString().trimEnd()
    }

    fun renderMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# Content Verification Report")
        sb.appendLine()
        sb.appendLine("Kotlin Compiler Server Version: $serverVersion")
        sb.appendLine()
        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine(renderSummaryTable())
        sb.appendLine()

        val errors = findings.filter { it.severity == Severity.ERROR }
        sb.appendLine("## Failures (${errors.size})")
        sb.appendLine()
        if (errors.isEmpty()) {
            sb.appendLine("No failures found.")
            sb.appendLine()
        } else {
            errors.forEachIndexed { index, finding ->
                val partSuffix = if (finding.partId != null) " (Part: `${finding.partId}`)" else ""
                sb.appendLine("### ${index + 1}. [ERROR] `${finding.exerciseId}`$partSuffix")
                sb.appendLine("- **File**: `${finding.chapterFile}`")
                sb.appendLine("- **Exercise ID**: `${finding.exerciseId}`")
                if (finding.partId != null) {
                    sb.appendLine("- **Part ID**: `${finding.partId}`")
                }
                sb.appendLine("- **Check**: ${finding.check}")
                sb.appendLine("- **Evidence**:")
                sb.appendLine("  ```")
                finding.evidence.lines().forEach { line ->
                    sb.appendLine("  $line")
                }
                sb.appendLine("  ```")
                sb.appendLine()
            }
        }

        val warnings = findings.filter { it.severity == Severity.WARNING }
        sb.appendLine("## Warnings (${warnings.size})")
        sb.appendLine()
        if (warnings.isEmpty()) {
            sb.appendLine("No warnings found.")
            sb.appendLine()
        } else {
            warnings.forEachIndexed { index, finding ->
                val partSuffix = if (finding.partId != null) " (Part: `${finding.partId}`)" else ""
                sb.appendLine("### ${index + 1}. [WARNING] `${finding.exerciseId}`$partSuffix")
                sb.appendLine("- **File**: `${finding.chapterFile}`")
                sb.appendLine("- **Exercise ID**: `${finding.exerciseId}`")
                if (finding.partId != null) {
                    sb.appendLine("- **Part ID**: `${finding.partId}`")
                }
                sb.appendLine("- **Check**: ${finding.check}")
                sb.appendLine("- **Evidence**:")
                sb.appendLine("  ```")
                finding.evidence.lines().forEach { line ->
                    sb.appendLine("  $line")
                }
                sb.appendLine("  ```")
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}

/**
 * Verifier that checks course exercises against the live kotlin-compiler-server.
 */
class ContentVerifier(private val client: CompilerServerClient) {

    fun verifyCourse(course: Course, courseDirName: String = "kotlin"): VerificationReport =
        verifyCourses(listOf(course), listOf(courseDirName))

    fun verifyCourses(courses: List<Course>, courseDirNames: List<String>): VerificationReport {
        val serverVersion = try {
            client.version()
        } catch (e: Exception) {
            "unavailable (${e.message})"
        }

        val allFindings = mutableListOf<VerificationFinding>()

        // Per-exercise-type counters
        val checkedCounts = mutableMapOf<String, Int>().withDefault { 0 }
        val passedCounts = mutableMapOf<String, Int>().withDefault { 0 }
        val failedCounts = mutableMapOf<String, Int>().withDefault { 0 }
        val warningCounts = mutableMapOf<String, Int>().withDefault { 0 }

        courses.forEachIndexed { courseIndex, course ->
            val courseDirName = courseDirNames.getOrElse(courseIndex) { course.id }
            for (chapter in course.chapters) {
                val chapterFile = "$courseDirName/chapters/${chapter.id}.yaml"
                for (lesson in chapter.lessons) {
                    for (exercise in lesson.exercises) {
                        val exerciseType = exerciseTypeName(exercise)

                        // Check if this exercise should be verified against compiler server
                        if (exercise is Exercise.MultipleChoice && exercise.code.isNullOrBlank()) {
                            // Multiple choice without code has no snippet to verify against compiler
                            continue
                        }

                        val exerciseFindings = mutableListOf<VerificationFinding>()
                        when (exercise) {
                            is Exercise.WriteFunction -> verifyWriteFunction(exercise, chapterFile, exerciseFindings)
                            is Exercise.MultiPartProblem -> verifyMultiPartProblem(exercise, chapterFile, exerciseFindings)
                            is Exercise.PredictOutput -> verifyPredictOutput(exercise, chapterFile, exerciseFindings)
                            is Exercise.MultipleChoice -> verifyMultipleChoice(exercise, chapterFile, exerciseFindings)
                            is Exercise.Cloze -> verifyCloze(exercise, chapterFile, exerciseFindings)
                        }

                        allFindings.addAll(exerciseFindings)

                        checkedCounts[exerciseType] = checkedCounts.getValue(exerciseType) + 1
                        val hasErrors = exerciseFindings.any { it.severity == Severity.ERROR }
                        if (hasErrors) {
                            failedCounts[exerciseType] = failedCounts.getValue(exerciseType) + 1
                        } else {
                            passedCounts[exerciseType] = passedCounts.getValue(exerciseType) + 1
                        }
                        val warnings = exerciseFindings.count { it.severity == Severity.WARNING }
                        warningCounts[exerciseType] = warningCounts.getValue(exerciseType) + warnings
                    }
                }
            }
        }

        val standardTypes = listOf(
            "write_function",
            "multi_part_problem",
            "predict_output",
            "multiple_choice",
            "cloze"
        )

        val summaries = standardTypes.map { type ->
            ExerciseTypeSummary(
                exerciseType = type,
                checked = checkedCounts.getValue(type),
                passed = passedCounts.getValue(type),
                failed = failedCounts.getValue(type),
                warnings = warningCounts.getValue(type)
            )
        }

        return VerificationReport(
            serverVersion = serverVersion,
            summaries = summaries,
            findings = allFindings
        )
    }

    private fun verifyWriteFunction(
        exercise: Exercise.WriteFunction,
        chapterFile: String,
        findings: MutableList<VerificationFinding>
    ) {
        val testClassName = extractTestClassName(exercise.hiddenTests)
        val testFileName = "$testClassName.kt"
        val expectedMethods = extractTestMethods(exercise.hiddenTests)

        // Check 1: Solution + tests
        val solutionResponse = client.test(
            listOf(
                ServerFile("Solution.kt", exercise.referenceSolution),
                ServerFile(testFileName, exercise.hiddenTests)
            )
        )
        val compileErrors = solutionResponse.errors.flatMap { (file, diags) ->
            diags.filter { it.severity.equals("ERROR", ignoreCase = true) }.map { file to it }
        }
        if (compileErrors.isNotEmpty()) {
            findings += VerificationFinding(
                severity = Severity.ERROR,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = "reference_solution compilation",
                evidence = compileErrors.joinToString("\n") { (file, diag) -> formatDiagnostic(file, diag) }
            )
        } else {
            val testResults = solutionResponse.testResults
            if (testResults == null || testResults.isEmpty()) {
                val errEvidence = solutionResponse.exception?.let { "${it.fullName}: ${it.message}" }
                    ?: solutionResponse.text?.takeIf { it.isNotBlank() }
                    ?: "No testResults returned from compiler server (missing or empty suite)"
                findings += VerificationFinding(
                    severity = Severity.ERROR,
                    chapterFile = chapterFile,
                    exerciseId = exercise.id,
                    partId = null,
                    check = "reference_solution tests",
                    evidence = errEvidence
                )
            } else {
                val allItems = testResults.values.flatten()
                val failingItems = allItems.filter { it.status != "OK" }
                if (failingItems.isNotEmpty()) {
                    findings += VerificationFinding(
                        severity = Severity.ERROR,
                        chapterFile = chapterFile,
                        exerciseId = exercise.id,
                        partId = null,
                        check = "reference_solution tests",
                        evidence = failingItems.joinToString("\n") { formatFailingTest(it) }
                    )
                }
                val returnedMethods = allItems.map { it.methodName }.toSet()
                if (expectedMethods.isEmpty()) {
                    findings += VerificationFinding(
                        severity = Severity.ERROR,
                        chapterFile = chapterFile,
                        exerciseId = exercise.id,
                        partId = null,
                        check = "reference_solution tests",
                        evidence = "No @Test methods found in hidden_tests"
                    )
                } else if (returnedMethods != expectedMethods) {
                    findings += VerificationFinding(
                        severity = Severity.ERROR,
                        chapterFile = chapterFile,
                        exerciseId = exercise.id,
                        partId = null,
                        check = "reference_solution tests",
                        evidence = "Returned test methods $returnedMethods did not match expected test methods $expectedMethods"
                    )
                }
            }
        }

        // Check 2: Starter code + tests compile
        val starterResponse = client.highlight(
            listOf(
                ServerFile("Solution.kt", exercise.starterCode),
                ServerFile(testFileName, exercise.hiddenTests)
            )
        )
        val starterErrors = starterResponse.flatMap { (file, diags) ->
            diags.filter { it.severity.equals("ERROR", ignoreCase = true) }.map { file to it }
        }
        if (starterErrors.isNotEmpty()) {
            findings += VerificationFinding(
                severity = Severity.ERROR,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = "starter_code compilation",
                evidence = starterErrors.joinToString("\n") { (file, diag) -> formatDiagnostic(file, diag) }
            )
        }
    }

    private fun verifyMultiPartProblem(
        exercise: Exercise.MultiPartProblem,
        chapterFile: String,
        findings: MutableList<VerificationFinding>
    ) {
        val partClassNames = exercise.parts.map { extractTestClassName(it.hiddenTests) }
        val duplicates = partClassNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            findings += VerificationFinding(
                severity = Severity.ERROR,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = "duplicate test class name",
                evidence = "Duplicate test class name(s) across parts: ${duplicates.joinToString(", ")}"
            )
        }

        // Part 1 starter code check
        val part1 = exercise.parts.firstOrNull()
        val starter = part1?.starterCode
        if (part1 != null && !starter.isNullOrBlank()) {
            val testFileName = "${partClassNames[0]}.kt"
            val starterResponse = client.highlight(
                listOf(
                    ServerFile("Solution.kt", starter),
                    ServerFile(testFileName, part1.hiddenTests)
                )
            )
            val starterErrors = starterResponse.flatMap { (file, diags) ->
                diags.filter { it.severity.equals("ERROR", ignoreCase = true) }.map { file to it }
            }
            if (starterErrors.isNotEmpty()) {
                findings += VerificationFinding(
                    severity = Severity.ERROR,
                    chapterFile = chapterFile,
                    exerciseId = exercise.id,
                    partId = part1.id,
                    check = "starter_code compilation",
                    evidence = starterErrors.joinToString("\n") { (file, diag) -> formatDiagnostic(file, diag) }
                )
            }
        }

        // Cumulative parts
        for (k in exercise.parts.indices) {
            val partK = exercise.parts[k]
            val files = mutableListOf(ServerFile("Solution.kt", partK.referenceSolution))
            val expectedMethods = mutableSetOf<String>()
            for (i in 0..k) {
                val partI = exercise.parts[i]
                val classNameI = partClassNames[i]
                files.add(ServerFile("$classNameI.kt", partI.hiddenTests))
                expectedMethods.addAll(extractTestMethods(partI.hiddenTests))
            }

            val response = client.test(files)
            val compileErrors = response.errors.flatMap { (file, diags) ->
                diags.filter { it.severity.equals("ERROR", ignoreCase = true) }.map { file to it }
            }
            if (compileErrors.isNotEmpty()) {
                findings += VerificationFinding(
                    severity = Severity.ERROR,
                    chapterFile = chapterFile,
                    exerciseId = exercise.id,
                    partId = partK.id,
                    check = "cumulative reference solution compilation",
                    evidence = compileErrors.joinToString("\n") { (file, diag) -> formatDiagnostic(file, diag) }
                )
            } else {
                val testResults = response.testResults
                if (testResults == null || testResults.isEmpty()) {
                    val errEvidence = response.exception?.let { "${it.fullName}: ${it.message}" }
                        ?: response.text?.takeIf { it.isNotBlank() }
                        ?: "No testResults returned from compiler server (missing or empty suite)"
                    findings += VerificationFinding(
                        severity = Severity.ERROR,
                        chapterFile = chapterFile,
                        exerciseId = exercise.id,
                        partId = partK.id,
                        check = "cumulative reference solution tests",
                        evidence = errEvidence
                    )
                } else {
                    val allItems = testResults.values.flatten()
                    val failingItems = allItems.filter { it.status != "OK" }
                    if (failingItems.isNotEmpty()) {
                        findings += VerificationFinding(
                            severity = Severity.ERROR,
                            chapterFile = chapterFile,
                            exerciseId = exercise.id,
                            partId = partK.id,
                            check = "cumulative reference solution tests",
                            evidence = failingItems.joinToString("\n") { formatFailingTest(it) }
                        )
                    }
                    val returnedMethods = allItems.map { it.methodName }.toSet()
                    if (expectedMethods.isEmpty()) {
                        findings += VerificationFinding(
                            severity = Severity.ERROR,
                            chapterFile = chapterFile,
                            exerciseId = exercise.id,
                            partId = partK.id,
                            check = "cumulative reference solution tests",
                            evidence = "No @Test methods found in hidden_tests for parts 1..${k + 1}"
                        )
                    } else if (returnedMethods != expectedMethods) {
                        findings += VerificationFinding(
                            severity = Severity.ERROR,
                            chapterFile = chapterFile,
                            exerciseId = exercise.id,
                            partId = partK.id,
                            check = "cumulative reference solution tests",
                            evidence = "Returned test methods $returnedMethods did not match expected test methods $expectedMethods"
                        )
                    }
                }
            }
        }
    }

    private fun verifyPredictOutput(
        exercise: Exercise.PredictOutput,
        chapterFile: String,
        findings: MutableList<VerificationFinding>
    ) {
        val response = client.run(listOf(ServerFile("Main.kt", exercise.code)))
        val compileErrors = response.errors.flatMap { (file, diags) ->
            diags.filter { it.severity.equals("ERROR", ignoreCase = true) }.map { file to it }
        }
        if (compileErrors.isNotEmpty()) {
            findings += VerificationFinding(
                severity = Severity.ERROR,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = "predict_output compilation",
                evidence = compileErrors.joinToString("\n") { (file, diag) -> formatDiagnostic(file, diag) }
            )
            return
        }

        if (response.exception != null) {
            findings += VerificationFinding(
                severity = Severity.ERROR,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = "predict_output execution",
                evidence = "${response.exception.fullName}: ${response.exception.message}"
            )
            return
        }

        val text = response.text.orEmpty()
        val errStreamMatches = Regex("<errStream>(.*?)</errStream>", RegexOption.DOT_MATCHES_ALL).findAll(text)
        val errStreamContent = errStreamMatches.map { it.groupValues[1] }.joinToString("")
        if (errStreamContent.isNotEmpty()) {
            findings += VerificationFinding(
                severity = Severity.ERROR,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = "predict_output stderr",
                evidence = "Stderr output encountered:\n$errStreamContent"
            )
            return
        }

        val outStreamMatches = Regex("<outStream>(.*?)</outStream>", RegexOption.DOT_MATCHES_ALL).findAll(text)
        val stdout = outStreamMatches.map { it.groupValues[1] }.joinToString("")
        val normStdout = normalizeOutput(stdout)
        val normExpected = normalizeOutput(exercise.expectedOutput)

        if (normStdout != normExpected) {
            findings += VerificationFinding(
                severity = Severity.ERROR,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = "predict_output output match",
                evidence = diffOutputs(normExpected, normStdout)
            )
        }
    }

    private fun verifyMultipleChoice(
        exercise: Exercise.MultipleChoice,
        chapterFile: String,
        findings: MutableList<VerificationFinding>
    ) {
        val code = exercise.code ?: return
        if (code.isBlank()) return

        val hasMain = Regex("""\bfun\s+main\b""").containsMatchIn(code)
        val response = client.highlight(listOf(ServerFile("Snippet.kt", wrapSnippetIfNeeded(code))))
        val errors = response.flatMap { (file, diags) ->
            diags.filter { it.severity.equals("ERROR", ignoreCase = true) }.map { file to it }
        }
        if (errors.isNotEmpty()) {
            val severity = if (hasMain) Severity.ERROR else Severity.WARNING
            val check = if (hasMain) "main function compilation" else "snippet syntax highlighting"
            findings += VerificationFinding(
                severity = severity,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = check,
                evidence = errors.joinToString("\n") { (file, diag) -> formatDiagnostic(file, diag) }
            )
        }
    }

    private fun verifyCloze(
        exercise: Exercise.Cloze,
        chapterFile: String,
        findings: MutableList<VerificationFinding>
    ) {
        var filled = exercise.template
        for (blank in exercise.blanks) {
            val answer = blank.accepted.firstOrNull().orEmpty()
            filled = filled.replace(blank.placeholder, answer)
        }

        val hasMain = Regex("""\bfun\s+main\b""").containsMatchIn(filled)
        val response = client.highlight(listOf(ServerFile("Snippet.kt", wrapSnippetIfNeeded(filled))))
        val errors = response.flatMap { (file, diags) ->
            diags.filter { it.severity.equals("ERROR", ignoreCase = true) }.map { file to it }
        }
        if (errors.isNotEmpty()) {
            val severity = if (hasMain) Severity.ERROR else Severity.WARNING
            val check = if (hasMain) "main function compilation" else "cloze snippet highlighting"
            findings += VerificationFinding(
                severity = severity,
                chapterFile = chapterFile,
                exerciseId = exercise.id,
                partId = null,
                check = check,
                evidence = errors.joinToString("\n") { (file, diag) -> formatDiagnostic(file, diag) }
            )
        }
    }

    companion object {
        fun exerciseTypeName(exercise: Exercise): String = when (exercise) {
            is Exercise.WriteFunction -> "write_function"
            is Exercise.MultiPartProblem -> "multi_part_problem"
            is Exercise.PredictOutput -> "predict_output"
            is Exercise.MultipleChoice -> "multiple_choice"
            is Exercise.Cloze -> "cloze"
        }

        fun extractTestClassName(hiddenTests: String): String {
            val testClassMatch = Regex("""\bclass\s+([A-Za-z0-9_]+Test)\b""").find(hiddenTests)
            if (testClassMatch != null) return testClassMatch.groupValues[1]
            val anyClassMatch = Regex("""\bclass\s+([A-Za-z0-9_]+)\b""").find(hiddenTests)
            return anyClassMatch?.groupValues?.get(1) ?: error("No class declaration found in hidden_tests")
        }

        fun extractTestMethods(hiddenTests: String): Set<String> {
            val matches = Regex("""@Test\s+(?:suspend\s+)?fun\s+([A-Za-z0-9_`]+)""").findAll(hiddenTests)
            return matches.map { it.groupValues[1].removeSurrounding("`") }.toSet()
        }

        private val topLevelDeclaration = Regex(
            """^(?:(?:private|internal|public|const|data|enum|sealed|abstract|open|inline|suspend|operator|infix|tailrec|annotation)\s+)*(?:fun|class|object|interface|val|var|typealias)\b|^import\s|^@"""
        )

        /**
         * Cloze and multiple-choice snippets are often statements, which the compiler reads as a file
         * and rejects with "Expecting a top level declaration". A snippet with no top-level declaration
         * is wrapped in a function so it highlights as the statements it is. Wrapping adds one line
         * before the snippet, so diagnostic line numbers for wrapped snippets are one higher than the YAML.
         */
        fun wrapSnippetIfNeeded(code: String): String {
            val hasTopLevelDeclaration = code.lineSequence().any { topLevelDeclaration.containsMatchIn(it) }
            return if (hasTopLevelDeclaration) code else "fun snippet() {\n$code\n}"
        }

        fun normalizeOutput(text: String): String {
            return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .joinToString("\n") { it.trimEnd() }
                .trimEnd('\n')
        }

        fun diffOutputs(expected: String, actual: String): String {
            val expLines = expected.lines()
            val actLines = actual.lines()
            val sb = StringBuilder()
            sb.appendLine("Output mismatch:")
            sb.appendLine("--- Expected (${expLines.size} lines)")
            sb.appendLine("+++ Actual (${actLines.size} lines)")
            val maxLines = maxOf(expLines.size, actLines.size)
            for (i in 0 until maxLines) {
                val exp = expLines.getOrNull(i)
                val act = actLines.getOrNull(i)
                if (exp != act) {
                    sb.appendLine("Line ${i + 1}:")
                    if (exp != null) sb.appendLine("  - $exp")
                    if (act != null) sb.appendLine("  + $act")
                }
            }
            return sb.toString().trimEnd()
        }

        fun formatDiagnostic(fileName: String, diagnostic: Diagnostic): String {
            val line1Based = (diagnostic.interval?.start?.line ?: 0) + 1
            return "$fileName:$line1Based: ${diagnostic.message}"
        }

        fun formatFailingTest(test: TestResult): String {
            val detail = test.comparisonFailure?.message
                ?: test.exception?.let { "${it.fullName}: ${it.message}" }
                ?: "status ${test.status}"
            return "${test.className}.${test.methodName}: $detail"
        }

        fun runFromCommandLine(contentDir: File, serverUrl: String, reportFile: File): Int {
            println("Verifying content in ${contentDir.absolutePath} against $serverUrl...")
            val log = ContentIssueLog()
            val courseDirs = contentDir.listFiles { file -> file.isDirectory && File(file, "course.yaml").isFile }
                .orEmpty()
                .sortedBy { it.name }

            if (courseDirs.isEmpty()) {
                System.err.println("No course folders found under ${contentDir.absolutePath}")
                return 1
            }

            val courses = courseDirs.mapNotNull { CourseCompiler(it, log).compile() }
            if (log.hasErrors) {
                System.err.println("Course decoding failed with errors:")
                log.issues.filter { it.severity == Severity.ERROR }.forEach { System.err.println(it.format()) }
                return 1
            }

            val client = HttpCompilerServerClient(serverUrl)
            val verifier = ContentVerifier(client)
            val report = verifier.verifyCourses(courses, courseDirs.map { it.name })

            // Ensure parent directory exists and write report
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(report.renderMarkdown())
            println("Wrote verification report to ${reportFile.absolutePath}")

            // Print summary table to console
            println()
            println(report.renderSummaryTable())
            println()

            val errors = report.findings.filter { it.severity == Severity.ERROR }
            val warnings = report.findings.filter { it.severity == Severity.WARNING }
            if (errors.isNotEmpty()) {
                System.err.println("Verification failed with ${errors.size} error(s):")
                errors.forEach { err ->
                    val part = if (err.partId != null) " (part ${err.partId})" else ""
                    System.err.println("  [ERROR] ${err.chapterFile}: ${err.exerciseId}$part: ${err.check}")
                    err.evidence.lines().forEach { line -> System.err.println("    $line") }
                }
            }
            if (warnings.isNotEmpty()) {
                println("Verification reported ${warnings.size} warning(s):")
                warnings.forEach { warn ->
                    val part = if (warn.partId != null) " (part ${warn.partId})" else ""
                    println("  [WARNING] ${warn.chapterFile}: ${warn.exerciseId}$part: ${warn.check}")
                }
            }

            if (report.hasErrors) {
                System.err.println("Content verification failed with ${errors.size} error(s), ${warnings.size} warning(s).")
                return 1
            }

            println("Content verification passed with ${warnings.size} warning(s).")
            return 0
        }
    }
}

/**
 * Command-line entry point used by the `:content:verifyContent` Gradle task.
 * Arguments:
 *  0: content directory (default "content")
 *  1: compiler server URL (default "http://127.0.0.1:8080")
 *  2: report output markdown file (default "docs/content-verification.md")
 */
fun main(args: Array<String>) {
    val contentDir = File(args.getOrElse(0) { "content" })
    val serverUrl = args.getOrElse(1) { "http://127.0.0.1:8080" }
    val reportFile = File(args.getOrElse(2) { "docs/content-verification.md" })
    exitProcess(ContentVerifier.runFromCommandLine(contentDir, serverUrl, reportFile))
}
