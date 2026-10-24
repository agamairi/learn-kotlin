package dev.agam.learn.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ktor client for Gemini's generateContent API. */
internal class GeminiClient(
    private val httpClient: HttpClient,
    private val model: String = AiProviderConfig.GEMINI_DEFAULT_MODEL
) : AiChatClient {

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
                        parts = listOf(GeminiPart(text = systemPrompt))
                    ),
                    contents = listOf(
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiPart(text = userPrompt))
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        maxOutputTokens = maxTokens,
                        temperature = if (model.startsWith("gemini-4")) null else 0.2
                    )
                )
            )
        }.getOrElse { return AiClientResult.Failure(networkFailure = true) }

        if (response.status.value !in 200..299) return failureFrom(response)

        return try {
            val parsed = response.body<GeminiResponse>()
            AiClientResult.Text(parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AiClientResult.Failure(networkFailure = true)
        }
    }

    override suspend fun testConnection(apiKey: String): AiClientResult {
        val response = httpClient.executeStatus(
            method = HttpMethod.Get,
            url = AiProviderConfig.GEMINI_MODELS_URL
        ) {
            header(AiProviderConfig.GEMINI_API_KEY_HEADER, apiKey)
        }.getOrElse { return AiClientResult.Failure(networkFailure = true) }

        return if (response.status.value in 200..299) AiClientResult.Text("") else failureFrom(response)
    }
}

@Serializable
private data class GeminiRequest(
    @SerialName("systemInstruction") val systemInstruction: GeminiSystemInstruction,
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig
)

@Serializable
private data class GeminiSystemInstruction(val parts: List<GeminiPart>)

@Serializable
private data class GeminiContent(val role: String, val parts: List<GeminiPart>)

@Serializable
private data class GeminiPart(val text: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class GeminiGenerationConfig(
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val temperature: Double? = null
)

@Serializable
private data class GeminiResponse(val candidates: List<GeminiCandidate>)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String? = null
)
