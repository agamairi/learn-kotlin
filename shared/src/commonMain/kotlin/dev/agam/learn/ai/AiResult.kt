package dev.agam.learn.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result wrapper for AI tutor network requests.
 * Ensures API key, network, or provider errors surface as friendly messages instead of crashes.
 */
@Serializable
sealed interface AiResult<out T> {

    @Serializable
    @SerialName("success")
    data class Success<out T>(val data: T) : AiResult<T>

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
        val cause: String? = null
    ) : AiResult<Nothing>
}
