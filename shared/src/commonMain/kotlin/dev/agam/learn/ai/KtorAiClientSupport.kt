package dev.agam.learn.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException

internal suspend fun HttpClient.executeStatus(
    method: HttpMethod,
    url: String,
    requestBuilder: HttpRequestBuilder.() -> Unit = {}
): Result<HttpResponse> {
    try {
        return Result.success(request {
            this.method = method
            this.url { _ -> takeFrom(Url(url)) }
            contentType(ContentType.Application.Json)
            header(AiProviderConfig.CONTENT_TYPE_HEADER, AiProviderConfig.CONTENT_TYPE_JSON)
            requestBuilder()
        })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        return Result.failure(error)
    }
}

internal fun requestFailure(error: Throwable): AiClientResult.Failure =
    when {
        error.hasExceptionNamed("SSLHandshakeException", "SSLException") ->
            AiClientResult.Failure(reason = AiFailureReason.SECURE_CONNECTION_FAILURE)
        error.hasExceptionNamed(
            "ConnectTimeoutException",
            "HttpRequestTimeoutException",
            "SocketTimeoutException"
        ) -> AiClientResult.Failure(reason = AiFailureReason.REQUEST_TIMEOUT)
        error.isTransportFailure() -> AiClientResult.Failure(networkFailure = true)
        else -> AiClientResult.Failure(reason = AiFailureReason.UNKNOWN)
    }

private fun Throwable.isTransportFailure(): Boolean {
    val transportExceptionNames = setOf(
        "IOException",
        "ConnectException",
        "NoRouteToHostException",
        "SocketException",
        "UnknownHostException",
        "UnresolvedAddressException"
    )
    var current: Throwable? = this
    while (current != null) {
        if (current::class.simpleName in transportExceptionNames) return true
        current = current.cause
    }
    return false
}

private fun Throwable.hasExceptionNamed(vararg names: String): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current::class.simpleName in names) return true
        current = current.cause
    }
    return false
}

internal suspend fun failureFrom(response: HttpResponse): AiClientResult.Failure {
    val statusCode = response.status.value
    // Inspect only stable provider error codes. Raw response bodies never leave this function.
    val safeErrorMarkers = try {
        response.bodyAsText()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ""
    }
    val reason = when {
        safeErrorMarkers.contains("API_KEY_INVALID", ignoreCase = true) ->
            AiFailureReason.INVALID_CREDENTIALS
        safeErrorMarkers.contains("insufficient_quota", ignoreCase = true) ||
            safeErrorMarkers.contains("billing_hard_limit", ignoreCase = true) ->
            AiFailureReason.RATE_LIMIT_OR_QUOTA
        statusCode == 401 -> AiFailureReason.INVALID_CREDENTIALS
        statusCode == 403 -> AiFailureReason.PERMISSION_DENIED
        statusCode == 402 -> AiFailureReason.CREDITS_REQUIRED
        statusCode == 404 -> AiFailureReason.INVALID_MODEL
        statusCode == 400 || statusCode == 422 -> AiFailureReason.INVALID_REQUEST
        statusCode == 408 || statusCode == 504 -> AiFailureReason.REQUEST_TIMEOUT
        statusCode == 429 -> AiFailureReason.RATE_LIMIT_OR_QUOTA
        statusCode == 529 || statusCode in 500..599 -> AiFailureReason.PROVIDER_UNAVAILABLE
        else -> AiFailureReason.UNKNOWN
    }
    return AiClientResult.Failure(statusCode = statusCode, reason = reason)
}

internal fun invalidResponse() = AiClientResult.Failure(reason = AiFailureReason.INVALID_RESPONSE)

internal fun emptyResponse() = AiClientResult.Failure(reason = AiFailureReason.EMPTY_RESPONSE)
