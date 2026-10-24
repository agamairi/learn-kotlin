package dev.agam.learn.content.compiler

import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/**
 * Real HTTP implementation of [CompilerServerClient] communicating with the kotlin-compiler-server.
 *
 * Requests are sent one at a time, with a 30-second timeout and one retry on timeout.
 */
class HttpCompilerServerClient(
    rawBaseUrl: String = "http://127.0.0.1:8080"
) : CompilerServerClient {

    private val baseUrl = rawBaseUrl.trimEnd('/')

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val requestLock = Any()

    override fun version(): String {
        val uri = URI.create("$baseUrl/versions")
        val body = sendWithRetry(
            HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .GET()
                .build()
        )
        val versions = json.decodeFromString<List<VersionInfo>>(body)
        return versions.firstOrNull()?.version ?: "unknown"
    }

    override fun run(files: List<ServerFile>): RunResponse {
        val uri = URI.create("$baseUrl/api/compiler/run")
        val requestBody = json.encodeToString(
            CompilerRequest.serializer(),
            CompilerRequest(files = files)
        )
        val responseBody = sendWithRetry(
            HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        )
        return json.decodeFromString(RunResponse.serializer(), responseBody)
    }

    override fun test(files: List<ServerFile>): TestResponse {
        val uri = URI.create("$baseUrl/api/compiler/test")
        val requestBody = json.encodeToString(
            CompilerRequest.serializer(),
            CompilerRequest(files = files)
        )
        val responseBody = sendWithRetry(
            HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        )
        return json.decodeFromString(TestResponse.serializer(), responseBody)
    }

    override fun highlight(files: List<ServerFile>): Map<String, List<Diagnostic>> {
        val uri = URI.create("$baseUrl/api/compiler/highlight")
        val requestBody = json.encodeToString(
            CompilerRequest.serializer(),
            CompilerRequest(files = files)
        )
        val responseBody = sendWithRetry(
            HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        )
        return json.decodeFromString(responseBody)
    }

    private fun sendWithRetry(request: HttpRequest): String {
        synchronized(requestLock) {
            var attempts = 0
            while (true) {
                attempts++
                try {
                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                    if (response.statusCode() !in 200..299) {
                        throw IOException(
                            "Compiler server returned HTTP ${response.statusCode()} for ${request.uri()}: ${response.body()}"
                        )
                    }
                    return response.body()
                } catch (e: Exception) {
                    val isTimeout = e is HttpTimeoutException ||
                        e is java.net.SocketTimeoutException ||
                        e.cause is java.net.SocketTimeoutException
                    if (isTimeout && attempts <= 1) {
                        continue
                    }
                    throw e
                }
            }
        }
    }

    companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
