package dev.agam.learn.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ktor client for OpenAI's Responses API. */
internal class OpenAiClient(
    private val httpClient: HttpClient,
    private val model: String = AiProviderConfig.OPENAI_DEFAULT_MODEL
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
            url = AiProviderConfig.OPENAI_RESPONSES_URL
        ) {
            header(AiProviderConfig.OPENAI_AUTHORIZATION_HEADER, "Bearer $apiKey")
            setBody(
                OpenAiRequest(
                    model = model,
                    instructions = systemPrompt,
                    input = userPrompt,
                    maxOutputTokens = maxTokens
                )
            )
        }.getOrElse { return requestFailure(it) }

        if (response.status.value !in 200..299) return failureFrom(response)

        return try {
            val parsed = responseJson.decodeFromString<OpenAiResponse>(response.bodyAsText())
            when (parsed.status) {
                "incomplete" -> return AiClientResult.Failure(
                    reason = AiFailureReason.INCOMPLETE_RESPONSE
                )
                // Do not surface the provider's failure payload or partial output as a success.
                "failed" -> return AiClientResult.Failure(reason = AiFailureReason.UNKNOWN)
            }
            val output = parsed.output
                .filter { it.type == "message" }
                .flatMap { it.content }
                .filter { it.type == "output_text" }
                .mapNotNull { it.text }
                .joinToString("")
                .trim()
            output.takeIf(String::isNotEmpty)?.let(AiClientResult::Text) ?: emptyResponse()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            invalidResponse()
        }
    }

    override suspend fun testConnection(apiKey: String): AiClientResult {
        val response = httpClient.executeStatus(
            method = HttpMethod.Get,
            url = AiProviderConfig.OPENAI_MODELS_URL
        ) {
            header(AiProviderConfig.OPENAI_AUTHORIZATION_HEADER, "Bearer $apiKey")
        }.getOrElse { return requestFailure(it) }

        return if (response.status.value in 200..299) AiClientResult.Text("") else failureFrom(response)
    }

    override suspend fun listModels(apiKey: String): AiClientResult {
        val response = httpClient.executeStatus(
            method = HttpMethod.Get,
            url = AiProviderConfig.OPENAI_MODELS_URL
        ) {
            header(AiProviderConfig.OPENAI_AUTHORIZATION_HEADER, "Bearer $apiKey")
        }.getOrElse { return requestFailure(it) }
        if (response.status.value !in 200..299) return failureFrom(response)

        return try {
            val models = responseJson.decodeFromString<OpenAiModelsResponse>(response.bodyAsText())
                .data.map { it.id }
                .filter(::isTextGenerationModel)
                .distinct()
            AiClientResult.Models(models)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            invalidResponse()
        }
    }

    private fun isTextGenerationModel(id: String): Boolean {
        val normalized = id.lowercase()
        val isGenerative = normalized.startsWith("gpt-") ||
            normalized.startsWith("chatgpt-") ||
            normalized.matches(Regex("o[1-9](?:[-.]|$).*"))
        val isSpecialized = listOf(
            "-audio", "-realtime", "-transcribe", "-tts", "-image", "-search", "-embedding"
        ).any(normalized::contains) || normalized.startsWith("whisper-") || normalized.startsWith("dall-e")
        return isGenerative && !isSpecialized
    }
}

@Serializable
private data class OpenAiRequest(
    val model: String,
    val instructions: String,
    val input: String,
    @SerialName("max_output_tokens") val maxOutputTokens: Int
)

@Serializable
private data class OpenAiResponse(
    val status: String? = null,
    @SerialName("incomplete_details") val incompleteDetails: OpenAiIncompleteDetails? = null,
    val output: List<OpenAiOutput> = emptyList()
)

@Serializable
private data class OpenAiIncompleteDetails(val reason: String? = null)

@Serializable
private data class OpenAiOutput(
    val type: String? = null,
    val content: List<OpenAiContent> = emptyList()
)

@Serializable
private data class OpenAiContent(
    val type: String? = null,
    val text: String? = null
)

@Serializable
private data class OpenAiModelsResponse(
    val data: List<OpenAiModel> = emptyList()
)

@Serializable
private data class OpenAiModel(val id: String)
