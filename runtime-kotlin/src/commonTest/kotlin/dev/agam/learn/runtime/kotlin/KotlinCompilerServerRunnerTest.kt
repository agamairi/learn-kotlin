package dev.agam.learn.runtime.kotlin

import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class KotlinCompilerServerRunnerTest {
    @Test
    fun correctTestsPassFromRecordedResponse() = networkTest {
        val runner = runner(RESPONSE_CORRECT)
        val outcome = runner.test(
            files = listOf(SourceFile("Solution.kt", "fun sum(a: Int, b: Int) = a + b")),
            tests = listOf(SourceFile("SumTest.kt", TEST_SOURCE))
        )

        val passed = assertIs<RunOutcome.Passed>(outcome)
        assertEquals(2, passed.testsRun)
    }

    @Test
    fun compileErrorsBecomeOneBasedDiagnostics() = networkTest {
        val outcome = runner(RESPONSE_COMPILE_ERROR).run(
            listOf(SourceFile("Solution.kt", "fun sum(a: Int) = a + \"bad\""))
        )

        val compileError = assertIs<RunOutcome.CompileError>(outcome)
        assertEquals(1, compileError.diagnostics.single().line)
        assertEquals(34, compileError.diagnostics.single().column)
        assertEquals("ERROR", compileError.diagnostics.single().severity)
    }

    @Test
    fun highlightDiagnosticsAreMappedAndWarningsAreKept() = networkTest {
        val diagnostics = runner(RESPONSE_HIGHLIGHT).diagnostics(
            listOf(SourceFile("Solution.kt", "fun broken() = Unit"))
        )

        assertEquals(2, diagnostics.size)
        assertEquals(2, diagnostics[0].line)
        assertEquals(9, diagnostics[0].column)
        assertEquals("WARNING", diagnostics[0].severity)
        assertEquals(3, diagnostics[1].line)
    }

    @Test
    fun failingTestUsesComparisonFailureMessage() = networkTest {
        val outcome = runner(RESPONSE_FAILING).test(
            listOf(SourceFile("Solution.kt", "fun sum(a: Int, b: Int) = 0")),
            listOf(SourceFile("SumTest.kt", TEST_SOURCE))
        )

        val failed = assertIs<RunOutcome.TestsFailed>(outcome)
        assertEquals("addsTwoNumbers", failed.failures.single().testName)
        assertTrue(failed.failures.single().message.contains("expected:<5>"))
    }

    @Test
    fun timeoutTextBecomesTimeoutOutcome() = networkTest {
        val outcome = runner(RESPONSE_TIMEOUT).test(
            listOf(SourceFile("Solution.kt", "fun sum(a: Int, b: Int) = 0")),
            listOf(SourceFile("SumTest.kt", TEST_SOURCE))
        )

        assertIs<RunOutcome.Timeout>(outcome)
    }

    @Test
    fun errorStatusBecomesFailedTest() = networkTest {
        val outcome = runner(RESPONSE_OOM).test(
            listOf(SourceFile("Solution.kt", "fun sum(a: Int, b: Int): Int = 0")),
            listOf(SourceFile("SumTest.kt", TEST_SOURCE))
        )

        val failed = assertIs<RunOutcome.TestsFailed>(outcome)
        assertEquals("java.lang.OutOfMemoryError", failed.failures.single { it.testName == "addsTwoNumbers" }.message)
        assertEquals("Expected test did not run.", failed.failures.single { it.testName == "addsNegatives" }.message)
    }

    @Test
    fun missingTestResultsNeverPass() = networkTest {
        val outcome = runner(RESPONSE_NO_TESTS).test(
            listOf(SourceFile("Solution.kt", "fun sum(a: Int, b: Int) = a + b")),
            listOf(SourceFile("SumTest.kt", TEST_SOURCE))
        )

        val unavailable = assertIs<RunOutcome.Unavailable>(outcome)
        assertTrue(unavailable.reason.contains("No tests ran"))
    }

    @Test
    fun networkFailureIsFriendlyUnavailable() = networkTest {
        val engine = MockEngine { error("connection refused") }
        val outcome = KotlinCompilerServerRunner("http://compiler", httpClient = HttpClient(engine)).run(
            listOf(SourceFile("Main.kt", "fun main() = Unit"))
        )

        assertIs<RunOutcome.Unavailable>(outcome)
    }

    @Test
    fun badFileNamesAreRejectedBeforeSending() = networkTest {
        val runner = runner(RESPONSE_CORRECT)
        assertFailsWith<IllegalArgumentException> {
            runner.run(listOf(SourceFile("../Solution.kt", "fun main() = Unit")))
        }
        assertFailsWith<IllegalArgumentException> {
            runner.run(listOf(SourceFile("Solution.txt", "fun main() = Unit")))
        }
    }

    @Test
    fun bearerTokenIsSent() = networkTest {
        var authorization: String? = null
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond(RESPONSE_RUN, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        KotlinCompilerServerRunner(
            "http://compiler/",
            token = "secret",
            httpClient = HttpClient(engine)
        ).run(listOf(SourceFile("Main.kt", "fun main() = Unit")))

        assertEquals("Bearer secret", authorization)
    }

    @Test
    fun builderUsesTestClassNameAndSolutionName() {
        val files = KotlinTestFileBuilder.build("fun sum(a: Int, b: Int) = a + b", TEST_SOURCE)
        assertEquals(listOf("Solution.kt", "SumTest.kt"), files.map(SourceFile::name))
    }

    /**
     * The runner applies withTimeout to real Ktor calls. Under runTest alone the timeout runs on
     * virtual time and fires before the mock engine answers, so the body runs on a real dispatcher.
     */
    private fun networkTest(block: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default) { block() }
    }

    private fun runner(response: String): KotlinCompilerServerRunner = KotlinCompilerServerRunner(
        baseUrl = "http://compiler",
        httpClient = HttpClient(MockEngine {
            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
    )

    private companion object {
        const val TEST_SOURCE = """
            import kotlin.test.*
            class SumTest {
                @Test fun addsTwoNumbers() { assertEquals(5, sum(2, 3)) }
                @Test fun addsNegatives() { assertEquals(-1, sum(2, -3)) }
            }
        """

        const val RESPONSE_CORRECT = """
            {"testResults":{"SumTest":[{"output":"","className":"SumTest","methodName":"addsTwoNumbers","executionTime":4,"exception":null,"comparisonFailure":null,"status":"OK"},{"output":"","className":"SumTest","methodName":"addsNegatives","executionTime":0,"exception":null,"comparisonFailure":null,"status":"OK"}]},"errors":{"Solution.kt":[],"SumTest.kt":[]}}
        """
        const val RESPONSE_COMPILE_ERROR = """
            {"errors":{"Solution.kt":[{"interval":{"start":{"line":0,"ch":33},"end":{"line":0,"ch":34}},"message":"type mismatch","severity":"ERROR","className":"Solution"}],"SumTest.kt":[]},"exception":null,"text":""}
        """
        const val RESPONSE_HIGHLIGHT = """
            {"Solution.kt":[{"interval":{"start":{"line":1,"ch":8},"end":{"line":1,"ch":14}},"message":"Variable is unused.","severity":"WARNING","className":"WARNING"},{"interval":{"start":{"line":2,"ch":15},"end":{"line":2,"ch":16}},"message":"Initializer type mismatch.","severity":"ERROR","className":"Solution"}]}
        """
        const val RESPONSE_FAILING = """
            {"testResults":{"SumTest":[{"output":"","className":"SumTest","methodName":"addsTwoNumbers","executionTime":1,"exception":null,"comparisonFailure":{"message":"expected:<5> but was:<0>","expected":"","actual":""},"status":"FAIL"},{"output":"","className":"SumTest","methodName":"addsNegatives","executionTime":0,"exception":null,"comparisonFailure":null,"status":"OK"}]},"errors":{"Solution.kt":[],"SumTest.kt":[]}}
        """
        const val RESPONSE_TIMEOUT = """
            {"errors":{"Solution.kt":[],"SumTest.kt":[]},"text":"<errStream>Evaluation stopped while it's taking too long</errStream>"}
        """
        const val RESPONSE_OOM = """
            {"testResults":{"SumTest":[{"output":"","className":"SumTest","methodName":"addsTwoNumbers","executionTime":29,"exception":{"message":"Java heap space","fullName":"java.lang.OutOfMemoryError"},"comparisonFailure":null,"status":"ERROR"}]},"errors":{"Solution.kt":[],"SumTest.kt":[]}}
        """
        const val RESPONSE_NO_TESTS = """
            {"errors":{"Solution.kt":[]}}
        """
        const val RESPONSE_RUN = """
            {"errors":{"Main.kt":[]},"exception":null,"text":"<outStream>Hello</outStream>"}
        """
    }
}
