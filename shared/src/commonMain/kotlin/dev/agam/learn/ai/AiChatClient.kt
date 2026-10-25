package dev.agam.learn.ai

/** A typed result returned by a provider before user-facing error mapping. */
internal sealed interface AiClientResult {
    data class Text(val value: String) : AiClientResult

    data class Models(val values: List<String>) : AiClientResult

    data class Failure(
        val statusCode: Int? = null,
        val networkFailure: Boolean = false,
        val reason: AiFailureReason = AiFailureReason.UNKNOWN
    ) : AiClientResult
}

/** Sanitized failure categories. Provider error bodies and messages are never passed to the UI. */
internal enum class AiFailureReason {
    UNKNOWN,
    INVALID_CREDENTIALS,
    PERMISSION_DENIED,
    INVALID_MODEL,
    INVALID_REQUEST,
    RATE_LIMIT_OR_QUOTA,
    CREDITS_REQUIRED,
    PROVIDER_UNAVAILABLE,
    REQUEST_TIMEOUT,
    SECURE_CONNECTION_FAILURE,
    EMPTY_RESPONSE,
    BLOCKED_RESPONSE,
    INCOMPLETE_RESPONSE,
    INVALID_RESPONSE
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

    suspend fun listModels(apiKey: String): AiClientResult
}
