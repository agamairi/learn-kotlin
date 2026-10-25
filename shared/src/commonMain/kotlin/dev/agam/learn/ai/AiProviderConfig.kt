package dev.agam.learn.ai

/**
 * Immutable wire configuration for the supported AI providers.
 *
 * Keeping these values together makes endpoint and model changes auditable and
 * prevents request code from silently drifting between providers.
 */
object AiProviderConfig {
    const val ANTHROPIC_MESSAGES_URL = "https://api.anthropic.com/v1/messages"
    const val ANTHROPIC_MODELS_URL = "https://api.anthropic.com/v1/models"
    const val ANTHROPIC_VERSION = "2026-10-21"
    const val ANTHROPIC_AUTHORIZATION_HEADER = "Authorization"
    const val ANTHROPIC_VERSION_HEADER = "anthropic-version"
    const val ANTHROPIC_DEFAULT_MODEL = "claude-haiku-5-5"
    const val ANTHROPIC_STRONG_MODEL = "claude-opus-5-5"

    const val OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"
    const val OPENAI_MODELS_URL = "https://api.openai.com/v1/models"
    const val OPENAI_AUTHORIZATION_HEADER = "Authorization"
    const val OPENAI_DEFAULT_MODEL = "gpt-6-luna"
    const val OPENAI_STRONG_MODEL = "gpt-6-astra"

    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    const val GEMINI_MODELS_URL = "$GEMINI_BASE_URL/models"
    const val GEMINI_API_KEY_HEADER = "x-goog-api-key"
    const val GEMINI_DEFAULT_MODEL = "gemini-3.5-flash-lite"
    const val GEMINI_FAST_MODEL = GEMINI_DEFAULT_MODEL

    const val OPENROUTER_CHAT_COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models"
    const val OPENROUTER_KEY_URL = "https://openrouter.ai/api/v1/key"
    const val OPENROUTER_AUTHORIZATION_HEADER = "Authorization"
    const val OPENROUTER_DEFAULT_MODEL = "openrouter/auto"

    const val CONTENT_TYPE_HEADER = "Content-Type"
    const val CONTENT_TYPE_JSON = "application/json"

    fun geminiGenerateContentUrl(model: String): String =
        "$GEMINI_BASE_URL/models/${normalizeModel(AiProvider.GEMINI, model)}:generateContent"

    /** Normalize text-field model IDs before sending them to provider APIs. */
    fun normalizeModel(provider: AiProvider, model: String?): String {
        val normalized = model?.trim()?.takeIf(String::isNotEmpty)
            ?: defaultModel(provider)
        return if (provider == AiProvider.GEMINI) normalized.removePrefix("models/") else normalized
    }

    fun defaultModel(provider: AiProvider): String = when (provider) {
        AiProvider.ANTHROPIC -> ANTHROPIC_DEFAULT_MODEL
        AiProvider.OPENAI -> OPENAI_DEFAULT_MODEL
        AiProvider.GEMINI -> GEMINI_DEFAULT_MODEL
        AiProvider.OPENROUTER -> OPENROUTER_DEFAULT_MODEL
    }
}
