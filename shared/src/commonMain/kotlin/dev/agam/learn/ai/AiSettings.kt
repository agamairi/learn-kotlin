package dev.agam.learn.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Local AI tutor configuration. The API key is deliberately not part of this type: it is read
 * from [dev.agam.learn.data.SecretStore] only, so it can never be serialized with these settings.
 */
@Serializable
data class AiSettings(
    val provider: AiProvider,
    val model: String? = null,
    val enabled: Boolean = true,
    /** When true, all tutor requests are blocked for interview practice. */
    @SerialName("interview_mode")
    val interviewMode: Boolean = false
)
