package dev.agam.learn.ai

import kotlinx.serialization.Serializable

/**
 * Supported LLM providers for the bring-your-own-key AI Tutor.
 */
@Serializable
enum class AiProvider {
    ANTHROPIC,
    OPENAI,
    GEMINI,
    OPENROUTER
}
