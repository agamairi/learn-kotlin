package dev.agam.learn.ui.settings

import dev.agam.learn.ai.AiProvider
import dev.agam.learn.ai.AiSettings
import dev.agam.learn.data.ProgressRepository

/**
 * Repository interface for managing learner settings including daily goal,
 * AI tutor credentials, compiler runner configuration, and interview mode preferences.
 *
 * Defined locally in package dev.agam.learn.ui.settings as specified in the assignment brief.
 */
interface SettingsRepository {
    /**
     * Retrieves the learner's daily target XP (default: 50).
     */
    suspend fun getDailyGoal(): Int

    /**
     * Updates the learner's daily target XP.
     */
    suspend fun setDailyGoal(goalXp: Int)

    /**
     * Retrieves current AI tutor settings and credentials, or null if unconfigured.
     */
    suspend fun getAiSettings(): AiSettings?

    /**
     * Saves AI tutor provider, API key, and preferred model.
     */
    suspend fun setAiSettings(settings: AiSettings)

    /**
     * Removes the stored AI tutor API key.
     */
    suspend fun clearAiKey()

    /**
     * Retrieves the compiler runner server URL (default: http://10.0.2.2:8080).
     */
    suspend fun getCompilerServerUrl(): String

    /**
     * Updates the compiler runner server URL.
     */
    suspend fun setCompilerServerUrl(url: String)

    /**
     * Retrieves the optional bearer authentication token for the compiler server.
     */
    suspend fun getCompilerServerToken(): String?

    /**
     * Updates the compiler server bearer authentication token.
     */
    suspend fun setCompilerServerToken(token: String?)

    /**
     * Returns whether problem sessions default to interview mode (no AI).
     */
    suspend fun isInterviewModeDefault(): Boolean

    /**
     * Sets whether problem sessions default to interview mode.
     */
    suspend fun setInterviewModeDefault(enabled: Boolean)
}

/**
 * Implementation of [SettingsRepository] backed by [ProgressRepository] key-value storage.
 */
class ProgressRepositorySettingsAdapter(
    private val progressRepository: ProgressRepository
) : SettingsRepository {

    override suspend fun getDailyGoal(): Int {
        return progressRepository.getSetting(KEY_DAILY_GOAL)?.toIntOrNull() ?: DEFAULT_DAILY_GOAL
    }

    override suspend fun setDailyGoal(goalXp: Int) {
        progressRepository.setSetting(KEY_DAILY_GOAL, goalXp.toString())
    }

    override suspend fun getAiSettings(): AiSettings? {
        val providerStr = progressRepository.getSetting(KEY_AI_PROVIDER) ?: return null
        val provider = try {
            AiProvider.valueOf(providerStr)
        } catch (_: Exception) {
            return null
        }
        val apiKey = progressRepository.getSetting(KEY_AI_API_KEY) ?: return null
        val model = progressRepository.getSetting(KEY_AI_MODEL)
        val enabled = progressRepository.getSetting(KEY_AI_ENABLED)?.toBooleanStrictOrNull() ?: true

        return AiSettings(
            provider = provider,
            apiKey = apiKey,
            model = model,
            enabled = enabled
        )
    }

    override suspend fun setAiSettings(settings: AiSettings) {
        progressRepository.setSetting(KEY_AI_PROVIDER, settings.provider.name)
        progressRepository.setSetting(KEY_AI_API_KEY, settings.apiKey)
        settings.model?.let { progressRepository.setSetting(KEY_AI_MODEL, it) }
        progressRepository.setSetting(KEY_AI_ENABLED, settings.enabled.toString())
    }

    override suspend fun clearAiKey() {
        progressRepository.setSetting(KEY_AI_API_KEY, "")
        progressRepository.setSetting(KEY_AI_ENABLED, "false")
    }

    override suspend fun getCompilerServerUrl(): String {
        return progressRepository.getSetting(KEY_COMPILER_URL) ?: DEFAULT_COMPILER_URL
    }

    override suspend fun setCompilerServerUrl(url: String) {
        progressRepository.setSetting(KEY_COMPILER_URL, url)
    }

    override suspend fun getCompilerServerToken(): String? {
        return progressRepository.getSetting(KEY_COMPILER_TOKEN)
    }

    override suspend fun setCompilerServerToken(token: String?) {
        progressRepository.setSetting(KEY_COMPILER_TOKEN, token ?: "")
    }

    override suspend fun isInterviewModeDefault(): Boolean {
        return progressRepository.getSetting(KEY_INTERVIEW_MODE)?.toBooleanStrictOrNull() ?: false
    }

    override suspend fun setInterviewModeDefault(enabled: Boolean) {
        progressRepository.setSetting(KEY_INTERVIEW_MODE, enabled.toString())
    }

    companion object {
        const val KEY_DAILY_GOAL = "settings.daily_goal"
        const val KEY_AI_PROVIDER = "settings.ai_provider"
        const val KEY_AI_API_KEY = "settings.ai_api_key"
        const val KEY_AI_MODEL = "settings.ai_model"
        const val KEY_AI_ENABLED = "settings.ai_enabled"
        const val KEY_COMPILER_URL = "settings.compiler_url"
        const val KEY_COMPILER_TOKEN = "settings.compiler_token"
        const val KEY_INTERVIEW_MODE = "settings.interview_mode_default"

        const val DEFAULT_DAILY_GOAL = 50
        const val DEFAULT_COMPILER_URL = "http://10.0.2.2:8080"
    }
}

/**
 * In-memory implementation of [SettingsRepository] for testing and previews.
 */
class InMemorySettingsRepository(
    initialGoal: Int = 50,
    initialAiSettings: AiSettings? = null,
    initialServerUrl: String = "http://10.0.2.2:8080",
    initialServerToken: String? = null,
    initialInterviewDefault: Boolean = false
) : SettingsRepository {
    private var dailyGoal: Int = initialGoal
    private var aiSettings: AiSettings? = initialAiSettings
    private var compilerUrl: String = initialServerUrl
    private var compilerToken: String? = initialServerToken
    private var interviewModeDefault: Boolean = initialInterviewDefault

    override suspend fun getDailyGoal(): Int = dailyGoal
    override suspend fun setDailyGoal(goalXp: Int) { dailyGoal = goalXp }

    override suspend fun getAiSettings(): AiSettings? = aiSettings
    override suspend fun setAiSettings(settings: AiSettings) { aiSettings = settings }
    override suspend fun clearAiKey() {
        aiSettings = aiSettings?.copy(apiKey = "", enabled = false)
    }

    override suspend fun getCompilerServerUrl(): String = compilerUrl
    override suspend fun setCompilerServerUrl(url: String) { compilerUrl = url }

    override suspend fun getCompilerServerToken(): String? = compilerToken
    override suspend fun setCompilerServerToken(token: String?) { compilerToken = token }

    override suspend fun isInterviewModeDefault(): Boolean = interviewModeDefault
    override suspend fun setInterviewModeDefault(enabled: Boolean) { interviewModeDefault = enabled }
}
