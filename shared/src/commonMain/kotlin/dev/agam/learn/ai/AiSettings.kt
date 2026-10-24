package dev.agam.learn.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Local AI tutor configuration and user credentials.
 */
@Serializable
data class AiSettings(
    val provider: AiProvider,
    @SerialName("api_key")
    val apiKey: String,
    val model: String? = null,
    val enabled: Boolean = true
)
