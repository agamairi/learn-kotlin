package dev.agam.learn.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ktor client for Anthropic's Messages API. */
internal class AnthropicAiClient(
    private val httpClient: HttpClient,
    private val model: String = AiProviderConfig.ANTHROPIC_DEFAULT_MODEL
) : AiChatClient {
    private val responseJson = Json { ignoreUnknownKeys = true }

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
        }.getOrElse { return requestFailure(it) }

        if (response.status.value !in 200..299) return failureFrom(response)

        return try {
            val parsed = responseJson.decodeFromString<AnthropicResponse>(response.bodyAsText())
            if (parsed.stopReason == "max_tokens") {
                return AiClientResult.Failure(reason = AiFailureReason.INCOMPLETE_RESPONSE)
            }

            val text = parsed.content
                .asSequence()
                .filter { it.type == "text" }
                .mapNotNull { it.text?.takeIf(String::isNotBlank) }
                .joinToString("")
                .trim()
            text.takeIf(String::isNotEmpty)?.let(AiClientResult::Text) ?: emptyResponse()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            invalidResponse()
        }
    }

    override suspend fun testConnection(apiKey: String): AiClientResult {
        val response = httpClient.executeStatus(
            method = HttpMethod.Get,
            url = AiProviderConfig.ANTHROPIC_MODELS_URL
        ) {
            header(AiProviderConfig.ANTHROPIC_AUTHORIZATION_HEADER, "Bearer $apiKey")
            header(AiProviderConfig.ANTHROPIC_VERSION_HEADER, AiProviderConfig.ANTHROPIC_VERSION)
        }.getOrElse { return requestFailure(it) }

        return if (response.status.value in 200..299) AiClientResult.Text("") else failureFrom(response)
    }

    override suspend fun listModels(apiKey: String): AiClientResult {
        val models = mutableListOf<String>()
        val seenCursors = mutableSetOf<String>()
        var afterId: String? = null
        var pageCount = 0

        while (pageCount < MAX_MODEL_PAGES) {
            val url = URLBuilder(AiProviderConfig.ANTHROPIC_MODELS_URL).apply {
                parameters.append("limit", MODEL_PAGE_SIZE.toString())
                afterId?.let { parameters.append("after_id", it) }
            }.buildString()
            val response = httpClient.executeStatus(HttpMethod.Get, url) {
                header(AiProviderConfig.ANTHROPIC_AUTHORIZATION_HEADER, "Bearer $apiKey")
                header(AiProviderConfig.ANTHROPIC_VERSION_HEADER, AiProviderConfig.ANTHROPIC_VERSION)
            }.getOrElse { return requestFailure(it) }
            if (response.status.value !in 200..299) return failureFrom(response)

            val page = try {
                responseJson.decodeFromString<AnthropicModelsResponse>(response.bodyAsText())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return invalidResponse()
            }
            models += page.data.map { it.id }

            val cursor = page.lastId?.takeIf { page.hasMore && seenCursors.add(it) } ?: break
            afterId = cursor
            pageCount++
        }

        return AiClientResult.Models(models.distinct())
    }

    private companion object {
        const val MODEL_PAGE_SIZE = 100
        const val MAX_MODEL_PAGES = 20
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
    val content: List<AnthropicResponseContent> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
private data class AnthropicResponseContent(
    val type: String? = null,
    val text: String? = null
)

@Serializable
private data class AnthropicModelsResponse(
    val data: List<AnthropicModel> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("last_id") val lastId: String? = null
)

@Serializable
private data class AnthropicModel(val id: String)
