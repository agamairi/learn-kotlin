package dev.agam.learn.content.compiler

import kotlinx.serialization.Serializable

@Serializable
data class ServerFile(
    val name: String,
    val text: String
)

@Serializable
data class CompilerRequest(
    val args: String = "",
    val files: List<ServerFile>
)

@Serializable
data class TextPosition(
    val line: Int = 0,
    val ch: Int = 0
)

@Serializable
data class TextInterval(
    val start: TextPosition? = null,
    val end: TextPosition? = null
)

@Serializable
data class Diagnostic(
    val interval: TextInterval? = null,
    val message: String = "",
    val severity: String = "",
    val className: String? = null
)

@Serializable
data class ServerException(
    val message: String? = null,
    val fullName: String? = null,
    val cause: String? = null,
    val localizedMessage: String? = null
)

@Serializable
data class ComparisonFailure(
    val message: String? = null,
    val fullName: String? = null,
    val cause: String? = null,
    val expected: String? = null,
    val actual: String? = null
)

@Serializable
data class TestResult(
    val output: String = "",
    val className: String = "",
    val methodName: String = "",
    val executionTime: Long = 0,
    val exception: ServerException? = null,
    val comparisonFailure: ComparisonFailure? = null,
    val status: String = ""
)

@Serializable
data class RunResponse(
    val errors: Map<String, List<Diagnostic>> = emptyMap(),
    val exception: ServerException? = null,
    val text: String? = null
)

@Serializable
data class TestResponse(
    val testResults: Map<String, List<TestResult>>? = null,
    val errors: Map<String, List<Diagnostic>> = emptyMap(),
    val exception: ServerException? = null,
    val text: String? = null
)

@Serializable
data class VersionInfo(
    val version: String = "",
    val stdlibVersion: String = ""
)

/**
 * Client interface for interacting with the JetBrains kotlin-compiler-server.
 */
interface CompilerServerClient {
    fun version(): String
    fun run(files: List<ServerFile>): RunResponse
    fun test(files: List<ServerFile>): TestResponse
    fun highlight(files: List<ServerFile>): Map<String, List<Diagnostic>>
}
