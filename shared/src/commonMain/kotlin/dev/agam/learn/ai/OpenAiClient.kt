package dev.agam.learn.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ktor client for OpenAI's Responses API. */
internal class OpenAiClient(
    private val httpClient: HttpClient,
    private val model: String = AiProviderConfig.OPENAI_DEFAULT_MODEL
) : AiChatClient {

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
                    maxOutputTokens = maxTokens,
                    temperature = 0.2
                )
            )
        }.getOrElse { return AiClientResult.Failure(networkFailure = true) }

        if (response.status.value !in 200..299) return failureFrom(response)

        return try {
            val parsed = response.body<OpenAiResponse>()
            AiClientResult.Text(parsed.output.firstOrNull()?.content?.firstOrNull()?.text.orEmpty())
        } catch (_: Throwable) {
            AiClientResult.Failure(networkFailure = true)
        }
    }

    override suspend fun testConnection(apiKey: String): AiClientResult {
        val response = httpClient.executeStatus(
            method = HttpMethod.Get,
            url = AiProviderConfig.OPENAI_MODELS_URL
        ) {
            header(AiProviderConfig.OPENAI_AUTHORIZATION_HEADER, "Bearer $apiKey")
        }.getOrElse { return AiClientResult.Failure(networkFailure = true) }

        return if (response.status.value in 200..299) AiClientResult.Text("") else failureFrom(response)
    }
}

@Serializable
private data class OpenAiRequest(
    val model: String,
    val instructions: String,
    val input: String,
    @SerialName("max_output_tokens") val maxOutputTokens: Int,
    val temperature: Double
)

@Serializable
private data class OpenAiResponse(
    val id: String? = null,
    @SerialName("object") val objectType: String? = null,
    val status: String? = null,
    val output: List<OpenAiOutput>
)

@Serializable
private data class OpenAiOutput(val type: String, val role: String, val content: List<OpenAiContent>)

@Serializable
private data class OpenAiContent(val type: String, val text: String)
