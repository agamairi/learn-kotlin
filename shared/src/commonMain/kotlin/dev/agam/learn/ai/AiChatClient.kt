package dev.agam.learn.ai

/** A typed result returned by a provider before user-facing error mapping. */
internal sealed interface AiClientResult {
    data class Text(val value: String) : AiClientResult

    data class Failure(
        val statusCode: Int? = null,
        val networkFailure: Boolean = false
    ) : AiClientResult
}

/** Common wire contract implemented by each vendor-specific client. */
internal interface AiChatClient {
    suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int
    ): AiClientResult

    suspend fun testConnection(apiKey: String): AiClientResult
}
