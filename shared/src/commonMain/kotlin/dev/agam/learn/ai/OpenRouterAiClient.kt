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

/** Ktor client for OpenRouter's OpenAI-compatible Chat Completions API. */
internal class OpenRouterAiClient(
    private val httpClient: HttpClient,
    private val model: String = AiProviderConfig.OPENROUTER_DEFAULT_MODEL
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
            url = AiProviderConfig.OPENROUTER_CHAT_COMPLETIONS_URL
        ) {
            header(AiProviderConfig.OPENROUTER_AUTHORIZATION_HEADER, "Bearer $apiKey")
            setBody(
                OpenRouterRequest(
                    model = model,
                    messages = listOf(
                        OpenRouterMessage(role = "system", content = systemPrompt),
                        OpenRouterMessage(role = "user", content = userPrompt)
                    ),
                    maxCompletionTokens = maxTokens
                )
            )
        }.getOrElse { return requestFailure(it) }

        if (response.status.value !in 200..299) return failureFrom(response)

        return try {
            val parsed = responseJson.decodeFromString<OpenRouterResponse>(response.bodyAsText())
            val text = parsed.choices.firstOrNull()?.message?.content
                ?.takeIf { it.isNotBlank() }
                ?: return emptyResponse()
            AiClientResult.Text(text)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A 2xx error object or malformed completion must not look like a successful answer.
            invalidResponse()
        }
    }

    override suspend fun testConnection(apiKey: String): AiClientResult {
        // The models endpoint is public, so it cannot establish that this key is valid.
        val response = httpClient.executeStatus(
            method = HttpMethod.Get,
            url = AiProviderConfig.OPENROUTER_KEY_URL
        ) {
            header(AiProviderConfig.OPENROUTER_AUTHORIZATION_HEADER, "Bearer $apiKey")
        }.getOrElse { return requestFailure(it) }

        return if (response.status.value in 200..299) AiClientResult.Text("") else failureFrom(response)
    }

    override suspend fun listModels(apiKey: String): AiClientResult {
        val url = URLBuilder(AiProviderConfig.OPENROUTER_MODELS_URL).apply {
            parameters.append("output_modalities", "text")
        }.buildString()
        val response = httpClient.executeStatus(HttpMethod.Get, url) {
            header(AiProviderConfig.OPENROUTER_AUTHORIZATION_HEADER, "Bearer $apiKey")
        }.getOrElse { return requestFailure(it) }
        if (response.status.value !in 200..299) return failureFrom(response)

        return try {
            val models = responseJson.decodeFromString<OpenRouterModelsResponse>(response.bodyAsText())
                .data
                .filter { model -> model.architecture?.outputModalities?.contains("text") != false }
                .map { it.id }
                .distinct()
            AiClientResult.Models(models)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            invalidResponse()
        }
    }
}

@Serializable
private data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int
)

@Serializable
private data class OpenRouterMessage(val role: String, val content: String)

@Serializable
private data class OpenRouterResponse(val choices: List<OpenRouterChoice>)

@Serializable
private data class OpenRouterChoice(val message: OpenRouterResponseMessage)

@Serializable
private data class OpenRouterResponseMessage(val content: String? = null)

@Serializable
private data class OpenRouterModelsResponse(val data: List<OpenRouterModel> = emptyList())

@Serializable
private data class OpenRouterModel(
    val id: String,
    val architecture: OpenRouterArchitecture? = null
)

@Serializable
private data class OpenRouterArchitecture(
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList()
)
