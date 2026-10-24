package dev.agam.learn.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ktor client for Anthropic's Messages API. */
internal class AnthropicAiClient(
    private val httpClient: HttpClient,
    private val model: String = AiProviderConfig.ANTHROPIC_DEFAULT_MODEL
) : AiChatClient {

    override suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int
    ): AiClientResult {
        val response = httpClient.executeStatus(
            method = HttpMethod.Post,
            url = AiProviderConfig.ANTHROPIC_MESSAGES_URL
        ) {
            header(AiProviderConfig.ANTHROPIC_AUTHORIZATION_HEADER, "Bearer $apiKey")
            header(AiProviderConfig.ANTHROPIC_VERSION_HEADER, AiProviderConfig.ANTHROPIC_VERSION)
            setBody(
                AnthropicRequest(
                    model = model,
                    maxTokens = maxTokens,
                    system = listOf(AnthropicSystemText(type = "text", text = systemPrompt)),
                    messages = listOf(AnthropicMessage(role = "user", content = userPrompt))
                )
            )
        }.getOrElse { return AiClientResult.Failure(networkFailure = true) }

        if (response.isFailure()) return failureFrom(response)

        return parseResponseBody(response)
    }

    override suspend fun testConnection(apiKey: String): AiClientResult {
        val response = httpClient.executeStatus(
            method = HttpMethod.Get,
            url = AiProviderConfig.ANTHROPIC_MODELS_URL
        ) {
            header(AiProviderConfig.ANTHROPIC_AUTHORIZATION_HEADER, "Bearer $apiKey")
            header(AiProviderConfig.ANTHROPIC_VERSION_HEADER, AiProviderConfig.ANTHROPIC_VERSION)
        }.getOrElse { return AiClientResult.Failure(networkFailure = true) }

        return if (response.isSuccess()) AiClientResult.Text("") else failureFrom(response)
    }

    private suspend fun parseResponseBody(response: io.ktor.client.statement.HttpResponse): AiClientResult =
        try {
            val parsed = response.body<AnthropicResponse>()
            AiClientResult.Text(parsed.content.firstOrNull()?.text.orEmpty())
        } catch (_: Throwable) {
            AiClientResult.Failure(networkFailure = true)
        }
}

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: List<AnthropicSystemText>,
    val messages: List<AnthropicMessage>
)

@Serializable
private data class AnthropicSystemText(val type: String, val text: String)

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicResponse(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val content: List<AnthropicResponseContent>,
    val model: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
private data class AnthropicResponseContent(val type: String, val text: String)

private fun io.ktor.client.statement.HttpResponse.isFailure(): Boolean =
    status.value !in 200..299

private fun io.ktor.client.statement.HttpResponse.isSuccess(): Boolean =
    status.value in 200..299
