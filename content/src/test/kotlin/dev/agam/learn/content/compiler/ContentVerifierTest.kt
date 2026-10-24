package dev.agam.learn.content.compiler

import dev.agam.learn.model.Blank
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runner.LanguageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentVerifierTest {

    private class FakeCompilerServerClient(
        var versionString: String = "2.5.0-Beta2",
        var runHandler: (List<ServerFile>) -> RunResponse = { RunResponse() },
        var testHandler: (List<ServerFile>) -> TestResponse = { TestResponse() },
        var highlightHandler: (List<ServerFile>) -> Map<String, List<Diagnostic>> = { emptyMap() }
    ) : CompilerServerClient {
        val runRequests = mutableListOf<List<ServerFile>>()
        val testRequests = mutableListOf<List<ServerFile>>()
        val highlightRequests = mutableListOf<List<ServerFile>>()

        override fun version(): String = versionString

        override fun run(files: List<ServerFile>): RunResponse {
            runRequests.add(files)
            return runHandler(files)
        }

        override fun test(files: List<ServerFile>): TestResponse {
            testRequests.add(files)
            return testHandler(files)
        }

        override fun highlight(files: List<ServerFile>): Map<String, List<Diagnostic>> {
            highlightRequests.add(files)
            return highlightHandler(files)
        }
    }

    @Test
    fun outputNormalizationHandlesCrlfTrailingWhitespaceAndTrailingNewlines() {
        val raw1 = "line 1   \r\nline 2\t  \r\n\r\n"
        val raw2 = "line 1\nline 2\n\n\n"
        val expected = "line 1\nline 2"

        assertEquals(expected, ContentVerifier.normalizeOutput(raw1))
        assertEquals(expected, ContentVerifier.normalizeOutput(raw2))

        val withMiddleEmpty = "line 1  \r\n\r\nline 2  \n"
        assertEquals("line 1\n\nline 2", ContentVerifier.normalizeOutput(withMiddleEmpty))

        assertEquals("", ContentVerifier.normalizeOutput("   \r\n  \n"))
        assertEquals("", ContentVerifier.normalizeOutput(""))
    }

    @Test
    fun testResultsMissingCaseIsReportedAsFailure() {
        val fakeClient = FakeCompilerServerClient(
            testHandler = { TestResponse(testResults = null) },
            highlightHandler = { emptyMap() }
        )

        val wf = Exercise.WriteFunction(
            id = "wf-missing-results",
            prompt = "Do something",
            starterCode = "fun run(): Int = TODO()",
            hiddenTests = "class SimpleTest { @Test fun testFoo() {} }",
            referenceSolution = "fun run(): Int = 1"
        )

        val course = createCourse(wf)
        val verifier = ContentVerifier(fakeClient)
        val report = verifier.verifyCourse(course)

        assertTrue(report.hasErrors)
        assertEquals(1, report.totalFailures)

        val finding = report.findings.single { it.severity == Severity.ERROR }
        assertEquals("wf-missing-results", finding.exerciseId)
        assertEquals("reference_solution tests", finding.check)
        assertTrue(finding.evidence.contains("No testResults returned from compiler server"))

        val wfSummary = report.summaries.first { it.exerciseType == "write_function" }
        assertEquals(1, wfSummary.checked)
        assertEquals(0, wfSummary.passed)
        assertEquals(1, wfSummary.failed)
    }

    @Test
    fun methodSetComparisonFlagsMismatchesAndFailingTests() {
        // Case 1: Mismatched methods (missing testTwo)
        val fakeClientMismatch = FakeCompilerServerClient(
            testHandler = {
                TestResponse(
                    testResults = mapOf(
                        "SampleTest" to listOf(
                            TestResult(className = "SampleTest", methodName = "testOne", status = "OK")
                        )
                    )
                )
            },
            highlightHandler = { emptyMap() }
        )

        val wf = Exercise.WriteFunction(
            id = "wf-method-mismatch",
            prompt = "Do something",
            starterCode = "fun run(): Int = TODO()",
            hiddenTests = """
                class SampleTest {
                    @Test fun testOne() {}
                    @Test fun testTwo() {}
                }
            """.trimIndent(),
            referenceSolution = "fun run(): Int = 1"
        )

        val verifierMismatch = ContentVerifier(fakeClientMismatch)
        val reportMismatch = verifierMismatch.verifyCourse(createCourse(wf))

        assertEquals(1, reportMismatch.totalFailures)
        val findingMismatch = reportMismatch.findings.single { it.severity == Severity.ERROR }
        assertTrue(findingMismatch.evidence.contains("Returned test methods [testOne] did not match expected test methods"))

        // Case 2: Matching methods but one fails
        val fakeClientFail = FakeCompilerServerClient(
            testHandler = {
                TestResponse(
                    testResults = mapOf(
                        "SampleTest" to listOf(
                            TestResult(className = "SampleTest", methodName = "testOne", status = "OK"),
                            TestResult(
                                className = "SampleTest",
                                methodName = "testTwo",
                                status = "FAIL",
                                comparisonFailure = ComparisonFailure(message = "expected:<1> but was:<2>")
                            )
                        )
                    )
                )
            },
            highlightHandler = { emptyMap() }
        )

        val verifierFail = ContentVerifier(fakeClientFail)
        val reportFail = verifierFail.verifyCourse(createCourse(wf))

        assertEquals(1, reportFail.totalFailures)
        val findingFail = reportFail.findings.single { it.severity == Severity.ERROR }
        assertTrue(findingFail.evidence.contains("SampleTest.testTwo: expected:<1> but was:<2>"))

        // Case 3: Matching methods and all OK -> passes
        val fakeClientPass = FakeCompilerServerClient(
            testHandler = {
                TestResponse(
                    testResults = mapOf(
                        "SampleTest" to listOf(
                            TestResult(className = "SampleTest", methodName = "testOne", status = "OK"),
                            TestResult(className = "SampleTest", methodName = "testTwo", status = "OK")
                        )
                    )
                )
            },
            highlightHandler = { emptyMap() }
        )

        val verifierPass = ContentVerifier(fakeClientPass)
        val reportPass = verifierPass.verifyCourse(createCourse(wf))

        assertEquals(0, reportPass.totalFailures)
        assertFalse(reportPass.hasErrors)
    }

    @Test
    fun cumulativePartRequestBuildingAccumulatesHiddenTestsAndDetectsDuplicates() {
        val fakeClient = FakeCompilerServerClient(
            testHandler = { files ->
                // Return OK for every test class in the files
                val testFiles = files.filter { it.name.endsWith("Test.kt") }
                val results = testFiles.associate { tf ->
                    val className = tf.name.removeSuffix(".kt")
                    className to listOf(TestResult(className = className, methodName = "testMethod", status = "OK"))
                }
                TestResponse(testResults = results)
            },
            highlightHandler = { emptyMap() }
        )

        val mpp = Exercise.MultiPartProblem(
            id = "mpp-cumulative",
            prompt = "Cumulative problem",
            story = "Story",
            parts = listOf(
                ProblemPart(
                    id = "part-1",
                    prompt = "Part 1",
                    starterCode = "fun p1() = 1",
                    hiddenTests = "class PartOneTest { @Test fun testMethod() {} }",
                    referenceSolution = "fun p1() = 1",
                    expected = "Expected 1"
                ),
                ProblemPart(
                    id = "part-2",
                    prompt = "Part 2",
                    hiddenTests = "class PartTwoTest { @Test fun testMethod() {} }",
                    referenceSolution = "fun p1() = 1; fun p2() = 2",
                    expected = "Expected 2"
                ),
                ProblemPart(
                    id = "part-3",
                    prompt = "Part 3",
                    hiddenTests = "class PartThreeTest { @Test fun testMethod() {} }",
                    referenceSolution = "fun p1() = 1; fun p2() = 2; fun p3() = 3",
                    expected = "Expected 3"
                )
            )
        )

        val verifier = ContentVerifier(fakeClient)
        val report = verifier.verifyCourse(createCourse(mpp))

        assertEquals(0, report.totalFailures)
        assertEquals(3, fakeClient.testRequests.size)

        // Request 1: Solution.kt + PartOneTest.kt
        val req1 = fakeClient.testRequests[0]
        assertEquals(listOf("Solution.kt", "PartOneTest.kt"), req1.map { it.name })
        assertEquals("fun p1() = 1", req1.first { it.name == "Solution.kt" }.text)

        // Request 2: Solution.kt + PartOneTest.kt + PartTwoTest.kt
        val req2 = fakeClient.testRequests[1]
        assertEquals(listOf("Solution.kt", "PartOneTest.kt", "PartTwoTest.kt"), req2.map { it.name })
        assertEquals("fun p1() = 1; fun p2() = 2", req2.first { it.name == "Solution.kt" }.text)

        // Request 3: Solution.kt + PartOneTest.kt + PartTwoTest.kt + PartThreeTest.kt
        val req3 = fakeClient.testRequests[2]
        assertEquals(listOf("Solution.kt", "PartOneTest.kt", "PartTwoTest.kt", "PartThreeTest.kt"), req3.map { it.name })
        assertEquals("fun p1() = 1; fun p2() = 2; fun p3() = 3", req3.first { it.name == "Solution.kt" }.text)

        // Test duplicate class name detection
        val mppDuplicate = Exercise.MultiPartProblem(
            id = "mpp-duplicate",
            prompt = "Duplicate class problem",
            story = "Story",
            parts = listOf(
                ProblemPart(
                    id = "part-a",
                    prompt = "Part A",
                    hiddenTests = "class SameTest { @Test fun testMethod() {} }",
                    referenceSolution = "fun p1() = 1",
                    expected = "Exp"
                ),
                ProblemPart(
                    id = "part-b",
                    prompt = "Part B",
                    hiddenTests = "class SameTest { @Test fun testMethod() {} }",
                    referenceSolution = "fun p1() = 1",
                    expected = "Exp"
                )
            )
        )

        val reportDuplicate = verifier.verifyCourse(createCourse(mppDuplicate))
        assertTrue(reportDuplicate.findings.any { it.check == "duplicate test class name" })
    }

    @Test
    fun reportPassFailCountsAndSummariesAreAccurate() {
        val fakeClient = FakeCompilerServerClient(
            runHandler = { files ->
                val code = files.first().text
                if (code.contains("fail")) {
                    RunResponse(text = "<outStream>actual output\n</outStream>")
                } else {
                    RunResponse(text = "<outStream>expected output\n</outStream>")
                }
            },
            testHandler = { files ->
                val solution = files.first { it.name == "Solution.kt" }.text
                val testFileNames = files.map { it.name }
                if (solution.contains("fail")) {
                    TestResponse(
                        testResults = mapOf(
                            "WfTest" to listOf(
                                TestResult(
                                    className = "WfTest",
                                    methodName = "testOk",
                                    status = "FAIL",
                                    comparisonFailure = ComparisonFailure(message = "expected:<1> but was:<0>")
                                )
                            )
                        )
                    )
                } else {
                    val map = mutableMapOf<String, List<TestResult>>()
                    if (testFileNames.contains("WfTest.kt")) {
                        map["WfTest"] = listOf(TestResult(className = "WfTest", methodName = "testOk", status = "OK"))
                    }
                    if (testFileNames.contains("PartTest.kt")) {
                        map["PartTest"] = listOf(TestResult(className = "PartTest", methodName = "testPart", status = "OK"))
                    }
                    TestResponse(testResults = map)
                }
            },
            highlightHandler = { files ->
                val text = files.first().text
                if (text.contains("warning-snippet")) {
                    mapOf(
                        "Snippet.kt" to listOf(
                            Diagnostic(
                                interval = TextInterval(start = TextPosition(line = 0, ch = 0)),
                                message = "Unused variable",
                                severity = "ERROR" // Cloze without main treats ERROR as WARNING
                            )
                        )
                    )
                } else {
                    emptyMap()
                }
            }
        )

        val passingWf = Exercise.WriteFunction(
            id = "wf-pass",
            prompt = "P",
            starterCode = "fun f() = 1",
            hiddenTests = "class WfTest { @Test fun testOk() {} }",
            referenceSolution = "fun f() = 1"
        )
        val failingWf = Exercise.WriteFunction(
            id = "wf-fail",
            prompt = "P",
            starterCode = "fun f() = 1",
            hiddenTests = "class WfTest { @Test fun testOk() {} }",
            referenceSolution = "fun f() = fail"
        )
        val passingPredict = Exercise.PredictOutput(
            id = "predict-pass",
            prompt = "P",
            code = "fun main() { println(\"expected output\") }",
            expectedOutput = "expected output",
            explanation = "E"
        )
        val failingPredict = Exercise.PredictOutput(
            id = "predict-fail",
            prompt = "P",
            code = "fun main() { println(\"fail\") }",
            expectedOutput = "expected output",
            explanation = "E"
        )
        val passingMpp = Exercise.MultiPartProblem(
            id = "mpp-pass",
            prompt = "P",
            story = "S",
            parts = listOf(
                ProblemPart(
                    id = "mpp-p1",
                    prompt = "P",
                    starterCode = "fun part() = 1",
                    hiddenTests = "class PartTest { @Test fun testPart() {} }",
                    referenceSolution = "fun part() = 1",
                    expected = "Exp"
                )
            )
        )
        val passingMcWithCode = Exercise.MultipleChoice(
            id = "mc-code-pass",
            prompt = "P",
            code = "val x = 1",
            options = listOf("1", "2"),
            correctIndex = 0,
            explanation = "E"
        )
        val warningCloze = Exercise.Cloze(
            id = "cloze-warn",
            prompt = "P",
            template = "val warning-snippet = ____1",
            blanks = listOf(Blank(placeholder = "____1", accepted = listOf("1"))),
            explanation = "E"
        )

        val course = createCourse(
            passingWf,
            failingWf,
            passingPredict,
            failingPredict,
            passingMpp,
            passingMcWithCode,
            warningCloze
        )

        val verifier = ContentVerifier(fakeClient)
        val report = verifier.verifyCourse(course)

        assertEquals(2, report.totalFailures)
        assertEquals(1, report.totalWarnings)
        assertTrue(report.hasErrors)

        val summaries = report.summaries.associateBy { it.exerciseType }

        // write_function: 2 checked, 1 passed, 1 failed, 0 warnings
        val wfSummary = summaries.getValue("write_function")
        assertEquals(2, wfSummary.checked)
        assertEquals(1, wfSummary.passed)
        assertEquals(1, wfSummary.failed)
        assertEquals(0, wfSummary.warnings)

        // predict_output: 2 checked, 1 passed, 1 failed, 0 warnings
        val predictSummary = summaries.getValue("predict_output")
        assertEquals(2, predictSummary.checked)
        assertEquals(1, predictSummary.passed)
        assertEquals(1, predictSummary.failed)
        assertEquals(0, predictSummary.warnings)

        // multi_part_problem: 1 checked, 1 passed, 0 failed, 0 warnings
        val mppSummary = summaries.getValue("multi_part_problem")
        assertEquals(1, mppSummary.checked)
        assertEquals(1, mppSummary.passed)
        assertEquals(0, mppSummary.failed)
        assertEquals(0, mppSummary.warnings)

        // multiple_choice: 1 checked (has code), 1 passed, 0 failed, 0 warnings
        val mcSummary = summaries.getValue("multiple_choice")
        assertEquals(1, mcSummary.checked)
        assertEquals(1, mcSummary.passed)
        assertEquals(0, mcSummary.failed)
        assertEquals(0, mcSummary.warnings)

        // cloze: 1 checked, 1 passed (no errors), 0 failed, 1 warning
        val clozeSummary = summaries.getValue("cloze")
        assertEquals(1, clozeSummary.checked)
        assertEquals(1, clozeSummary.passed)
        assertEquals(0, clozeSummary.failed)
        assertEquals(1, clozeSummary.warnings)

        val markdown = report.renderMarkdown()
        assertTrue(markdown.contains("| write_function | 2 | 1 | 1 | 0 |"))
        assertTrue(markdown.contains("| predict_output | 2 | 1 | 1 | 0 |"))
        assertTrue(markdown.contains("| multi_part_problem | 1 | 1 | 0 |"))
        assertTrue(markdown.contains("| multiple_choice | 1 | 1 | 0 |"))
        assertTrue(markdown.contains("| cloze | 1 | 1 | 0 | 1 |"))
    }

    private fun createCourse(vararg exercises: Exercise): Course {
        val chapter = Chapter(
            id = "ch01",
            title = "Chapter 1",
            summary = "Summary",
            lessons = listOf(
                Lesson(
                    id = "lesson-1",
                    title = "Lesson 1",
                    explanation = "Explanation",
                    exercises = exercises.toList()
                )
            )
        )
        return Course(
            id = "test-course",
            language = LanguageId("kotlin"),
            title = "Test Course",
            description = "Description",
            chapters = listOf(chapter)
        )
    }
}
