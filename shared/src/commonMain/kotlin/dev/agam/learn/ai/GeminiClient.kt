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

/** Ktor client for Gemini's generateContent API. */
internal class GeminiClient(
    private val httpClient: HttpClient,
    private val model: String = AiProviderConfig.GEMINI_DEFAULT_MODEL
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
            url = AiProviderConfig.geminiGenerateContentUrl(model)
        ) {
            header(AiProviderConfig.GEMINI_API_KEY_HEADER, apiKey)
            setBody(
                GeminiRequest(
                    systemInstruction = GeminiSystemInstruction(
                        parts = listOf(GeminiRequestPart(text = systemPrompt))
                    ),
                    contents = listOf(
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiRequestPart(text = userPrompt))
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(maxOutputTokens = maxTokens)
                )
            )
        }.getOrElse { return requestFailure(it) }

        if (response.status.value !in 200..299) return failureFrom(response)

        return try {
            val parsed = responseJson.decodeFromString<GeminiResponse>(response.bodyAsText())
            val candidate = parsed.candidates.firstOrNull()
            when {
                parsed.promptFeedback?.blockReason?.isBlockedReason() == true ->
                    AiClientResult.Failure(reason = AiFailureReason.BLOCKED_RESPONSE)
                candidate?.finishReason?.isBlockedReason() == true ->
                    AiClientResult.Failure(reason = AiFailureReason.BLOCKED_RESPONSE)
                candidate?.finishReason?.equals("MAX_TOKENS", ignoreCase = true) == true ->
                    AiClientResult.Failure(reason = AiFailureReason.INCOMPLETE_RESPONSE)
                else -> {
                    val text = candidate?.content?.parts.orEmpty()
                        .asSequence()
                        .filterNot { it.thought }
                        .mapNotNull { it.text }
                        .joinToString("")
                        .trim()
                    text.takeIf(String::isNotEmpty)?.let(AiClientResult::Text) ?: emptyResponse()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            invalidResponse()
        }
    }

    override suspend fun testConnection(apiKey: String): AiClientResult {
        val response = httpClient.executeStatus(
            method = HttpMethod.Get,
            url = AiProviderConfig.GEMINI_MODELS_URL
        ) {
            header(AiProviderConfig.GEMINI_API_KEY_HEADER, apiKey)
        }.getOrElse { return requestFailure(it) }

        return if (response.status.value in 200..299) AiClientResult.Text("") else failureFrom(response)
    }

    override suspend fun listModels(apiKey: String): AiClientResult {
        val models = mutableListOf<String>()
        val seenCursors = mutableSetOf<String>()
        var pageToken: String? = null
        var pageCount = 0

        while (pageCount < MAX_MODEL_PAGES) {
            val url = URLBuilder(AiProviderConfig.GEMINI_MODELS_URL).apply {
                parameters.append("pageSize", MODEL_PAGE_SIZE.toString())
                pageToken?.let { parameters.append("pageToken", it) }
            }.buildString()
            val response = httpClient.executeStatus(HttpMethod.Get, url) {
                header(AiProviderConfig.GEMINI_API_KEY_HEADER, apiKey)
            }.getOrElse { return requestFailure(it) }
            if (response.status.value !in 200..299) return failureFrom(response)

            val page = try {
                responseJson.decodeFromString<GeminiModelsResponse>(response.bodyAsText())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return invalidResponse()
            }
            models += page.models
                .filter { model -> model.supportedGenerationMethods.contains("generateContent") }
                .mapNotNull { model ->
                    model.name?.removePrefix("models/")?.takeIf(String::isNotBlank)
                        ?: model.baseModelId?.takeIf(String::isNotBlank)
                }

            val nextToken = page.nextPageToken?.takeIf { it.isNotBlank() && seenCursors.add(it) } ?: break
            pageToken = nextToken
            pageCount++
        }

        return AiClientResult.Models(models.distinct())
    }

    private fun String.isBlockedReason(): Boolean = uppercase() in BLOCKED_REASONS

    private companion object {
        const val MODEL_PAGE_SIZE = 1000
        const val MAX_MODEL_PAGES = 20
        val BLOCKED_REASONS = setOf(
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY"
        )
    }
}

@Serializable
private data class GeminiRequest(
    @SerialName("systemInstruction") val systemInstruction: GeminiSystemInstruction,
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig
)

@Serializable
private data class GeminiSystemInstruction(val parts: List<GeminiRequestPart>)

@Serializable
private data class GeminiContent(
    val role: String,
    val parts: List<GeminiRequestPart>
)

@Serializable
private data class GeminiRequestPart(val text: String)

@Serializable
private data class GeminiGenerationConfig(
    @SerialName("maxOutputTokens") val maxOutputTokens: Int
)

@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
private data class GeminiPromptFeedback(val blockReason: String? = null)

@Serializable
private data class GeminiCandidate(
    val content: GeminiResponseContent? = null,
    val finishReason: String? = null
)

@Serializable
private data class GeminiResponseContent(
    val parts: List<GeminiResponsePart> = emptyList()
)

@Serializable
private data class GeminiResponsePart(
    val text: String? = null,
    val thought: Boolean = false
)

@Serializable
private data class GeminiModelsResponse(
    val models: List<GeminiModel> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
private data class GeminiModel(
    val name: String? = null,
    val baseModelId: String? = null,
    val supportedGenerationMethods: List<String> = emptyList()
)
