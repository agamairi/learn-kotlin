package dev.agam.learn.runtime.kotlin

import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.Diagnostic
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile
import dev.agam.learn.runner.TestFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Ktor client for the single-user JetBrains kotlin-compiler-server.
 *
 * The server is intended to remain on loopback (or an equivalently private network) and is
 * protected by an optional bearer token. Results are not tamper-proof: the spike found that
 * learner code can forge the server's JUnit result channel. This class therefore provides
 * convenience and resource limits, not a sandbox or a security boundary.
 *
 * @param baseUrl Base URL of the compiler server, for example `http://10.0.2.2:8080`.
 * @param token Optional bearer token sent on every request.
 * @param httpClient Ktor client, injectable for platform wiring and tests.
 */
class KotlinCompilerServerRunner(
    private val baseUrl: String,
    private val token: String? = null,
    private val httpClient: HttpClient = HttpClient()
) : CodeRunner {

    override suspend fun run(files: List<SourceFile>): RunOutcome {
        val response = postJson("/api/compiler/run", requestBody(files, includeCompilerArguments = true))
            ?: return RunOutcome.Unavailable("Compiler server is unavailable.")
        val decoded = response.decodeOrUnavailable() ?: return RunOutcome.Unavailable(INVALID_RESPONSE)
        val text = decoded.text.orEmpty()

        timeoutOutcome(text)?.let { return it }
        val compileErrors = decoded.compileErrorsFor(files.map(SourceFile::name).toSet())
        if (compileErrors.isNotEmpty()) return RunOutcome.CompileError(compileErrors)
        decoded.exception?.let { return RunOutcome.Unavailable(it.friendlyReason()) }

        return RunOutcome.Passed(testsRun = 0, stdout = stripStreamTags(text))
    }

    override suspend fun test(files: List<SourceFile>, tests: List<SourceFile>): RunOutcome {
        val allFiles = files + tests
        val response = postJson("/api/compiler/test", requestBody(allFiles, includeCompilerArguments = true))
            ?: return RunOutcome.Unavailable("Compiler server is unavailable.")
        val decoded = response.decodeOrUnavailable() ?: return RunOutcome.Unavailable(INVALID_RESPONSE)
        val text = decoded.text.orEmpty()

        timeoutOutcome(text)?.let { return it }

        val learnerErrors = decoded.compileErrorsFor(files.map(SourceFile::name).toSet())
        val testErrors = decoded.compileErrorsFor(tests.map(SourceFile::name).toSet())
        if (learnerErrors.isNotEmpty()) return RunOutcome.CompileError(learnerErrors)
        if (testErrors.isNotEmpty()) {
            return RunOutcome.Unavailable("The hidden test source could not be compiled.")
        }
        decoded.exception?.let { return RunOutcome.Unavailable(it.friendlyReason()) }

        val resultEntries = decoded.testResults.orEmpty().values.flatten()
        if (resultEntries.isEmpty()) return RunOutcome.Unavailable("No tests ran.")

        val expectedNames = tests.flatMap { KotlinTestFileBuilder.testMethodNames(it) }.toSet()
        if (expectedNames.isEmpty()) {
            return RunOutcome.Unavailable("No runnable tests were found.")
        }

        val failures = buildList {
            resultEntries
                .filter { it.status.uppercase() != OK_STATUS }
                .forEach { add(TestFailure(it.testName(), it.failureMessage())) }

            val actualNames = resultEntries.mapNotNull { it.methodName }.toSet()
            expectedNames
                .filterNot(actualNames::contains)
                .forEach { add(TestFailure(it, "Expected test did not run.")) }
        }
        if (failures.isNotEmpty()) {
            return RunOutcome.TestsFailed(failures, stripStreamTags(text))
        }

        return RunOutcome.Passed(resultEntries.size, stripStreamTags(text))
    }

    override suspend fun diagnostics(files: List<SourceFile>): List<Diagnostic> {
        val response = postJson("/api/compiler/highlight", requestBody(files, includeCompilerArguments = false))
            ?: return emptyList()
        return try {
            json.decodeFromString<Map<String, List<ServerDiagnostic>>>(response)
                .values
                .flatten()
                .map(ServerDiagnostic::toDiagnostic)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun postJson(path: String, body: String): String? {
        return try {
            withTimeout(REQUEST_TIMEOUT_MILLIS) {
                val response = httpClient.post(baseUrl.trimEnd('/') + path) {
                    contentType(ContentType.Application.Json)
                    if (!token.isNullOrBlank()) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(body)
                }
                if (response.status.value !in 200..299) return@withTimeout null
                response.bodyAsText()
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }

    private fun requestBody(files: List<SourceFile>, includeCompilerArguments: Boolean): String {
        validateFiles(files)
        val body = buildJsonObject {
            put("args", "")
            put("files", buildJsonArray {
                files.forEach { file ->
                    add(buildJsonObject {
                        put("name", file.name)
                        put("text", file.text)
                    })
                }
            })
            if (includeCompilerArguments) put("compilerArguments", JsonObject(emptyMap()))
        }.toString()
        require(body.encodeToByteArray().size <= MAX_PAYLOAD_BYTES) {
            "Compiler request exceeds the ${MAX_PAYLOAD_BYTES / 1024} KB limit."
        }
        return body
    }

    private fun validateFiles(files: List<SourceFile>) {
        require(files.all { file ->
            file.name.isNotBlank() &&
                !file.name.contains('/') &&
                !file.name.contains('\\') &&
                !file.name.contains("..") &&
                file.name.endsWith(".kt")
        }) { "Kotlin source files must use plain .kt file names." }
        require(files.map(SourceFile::name).distinct().size == files.size) {
            "Kotlin source file names must be unique."
        }
    }

    private fun String.decodeOrUnavailable(): ServerResponse? = try {
        json.decodeFromString<ServerResponse>(this)
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val MAX_PAYLOAD_BYTES = 200 * 1024
        const val REQUEST_TIMEOUT_MILLIS = 15_000L
        const val OK_STATUS = "OK"
        const val INVALID_RESPONSE = "Compiler server returned an invalid response."
        val json = Json { ignoreUnknownKeys = true }

        fun timeoutOutcome(text: String): RunOutcome.Timeout? {
            val plainText = stripStreamTags(text).lowercase()
            return when {
                "evaluation stopped while it's taking too long" in plainText -> RunOutcome.Timeout(10)
                "log size exceeded" in plainText -> RunOutcome.Timeout(10)
                else -> null
            }
        }

        fun stripStreamTags(text: String): String = text
            .replace("<outStream>", "")
            .replace("</outStream>", "")
            .replace("<errStream>", "")
            .replace("</errStream>", "")
    }
}

@Serializable
private data class ServerResponse(
    val errors: Map<String, List<ServerDiagnostic>>? = null,
    val exception: ServerException? = null,
    val testResults: Map<String, List<ServerTestResult>>? = null,
    val text: String? = null
) {
    fun compileErrorsFor(fileNames: Set<String>): List<Diagnostic> = errors.orEmpty()
        .filterKeys(fileNames::contains)
        .values
        .flatten()
        .filter { it.severity.equals("ERROR", ignoreCase = true) }
        .map(ServerDiagnostic::toDiagnostic)
}

@Serializable
private data class ServerDiagnostic(
    val interval: ServerInterval = ServerInterval(),
    val message: String = "",
    val severity: String = "ERROR"
) {
    fun toDiagnostic(): Diagnostic = Diagnostic(
        line = interval.start.line + 1,
        column = interval.start.ch + 1,
        severity = severity,
        message = message
    )
}

@Serializable
private data class ServerInterval(
    val start: ServerPosition = ServerPosition(),
    val end: ServerPosition = ServerPosition()
)

@Serializable
private data class ServerPosition(
    val line: Int = 0,
    val ch: Int = 0
)

@Serializable
private data class ServerException(
    val message: String? = null,
    val fullName: String? = null
) {
    fun friendlyReason(): String {
        if (fullName == "com.fasterxml.jackson.databind.exc.MismatchedInputException" &&
            message.orEmpty().contains("No content to map")
        ) {
            return "The program ended the process early."
        }
        return listOfNotNull(message?.takeIf(String::isNotBlank), fullName)
            .firstOrNull()
            ?.let { "Compiler server error: $it" }
            ?: "Compiler server failed while running the code."
    }
}

@Serializable
private data class ServerTestResult(
    val className: String? = null,
    val methodName: String? = null,
    val status: String = "ERROR",
    val exception: ServerException? = null,
    val comparisonFailure: ServerComparisonFailure? = null
) {
    fun testName(): String = methodName ?: className ?: "unknown test"

    fun failureMessage(): String {
        comparisonFailure?.let {
            it.message?.takeIf(String::isNotBlank)?.let { message -> return message }
            val expected = it.expected.displayValue()
            val actual = it.actual.displayValue()
            if (expected != null || actual != null) {
                return "Expected ${expected ?: "<missing>"}, but was ${actual ?: "<missing>"}."
            }
        }
        exception?.fullName?.takeIf(String::isNotBlank)?.let { return it }
        exception?.message?.takeIf(String::isNotBlank)?.let { return it }
        return "Test ${status.lowercase()}."
    }
}

@Serializable
private data class ServerComparisonFailure(
    val message: String? = null,
    val expected: JsonElement? = null,
    val actual: JsonElement? = null
)

private fun JsonElement?.displayValue(): String? = when (this) {
    null -> null
    is JsonPrimitive -> contentOrNull
    else -> toString()
}
