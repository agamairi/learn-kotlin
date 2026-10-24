package dev.agam.learn.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.takeFrom

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
    } catch (error: Throwable) {
        return Result.failure(error)
    }
}

internal fun failureFrom(response: HttpResponse): AiClientResult.Failure =
    AiClientResult.Failure(statusCode = response.status.value)
