package dev.agam.learn.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.agam.learn.ai.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-configurable local application settings. */
data class AppSettings(
    val dailyGoalXp: Int = 50,
    val selectedAiProvider: AiProvider = AiProvider.ANTHROPIC,
    val selectedAiModel: String? = null,
    val compilerServerUrl: String = "",
    val compilerServerToken: String? = null,
    val interviewMode: Boolean = false
) {
    public companion object {
        public const val DEFAULT_DAILY_GOAL_XP: Int = 50
    }
}

/** Local persistence for the settings shown in the Settings screen. */
interface SettingsRepository {
    /** Reads all settings, applying product defaults for missing or invalid values. */
    suspend fun getSettings(): AppSettings

    /** Observes settings changes from the SQLDelight settings table. */
    fun observeSettings(): Flow<AppSettings>

    /** Updates the daily XP target. */
    suspend fun setDailyGoalXp(xp: Int)

    /** Updates the selected provider and optional model. */
    suspend fun setAiProvider(provider: AiProvider, model: String?)

    /** Updates the compiler server URL and optional bearer token. */
    suspend fun setCompilerServer(url: String, token: String?)

    /** Enables or disables interview mode. */
    suspend fun setInterviewMode(enabled: Boolean)
}

/** SQLDelight-backed implementation of [SettingsRepository]. */
class SqlDelightSettingsRepository(
    private val database: LearnDatabase
) : SettingsRepository {
    private val settings = database.settingsKvQueries

    override suspend fun getSettings(): AppSettings =
        settings.selectAllSettings().executeAsList()
            .associate { row -> row.key to row.value_ }
            .toSettings()

    override fun observeSettings(): Flow<AppSettings> =
        settings.selectAllSettings().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows
                .associate { row -> row.key to row.value_ }
                .toSettings()
        }

    override suspend fun setDailyGoalXp(xp: Int) {
        require(xp >= 0) { "Daily XP goal cannot be negative" }
        put(SettingKey.DAILY_GOAL_XP, xp.toString())
    }

    override suspend fun setAiProvider(provider: AiProvider, model: String?) {
        put(SettingKey.AI_PROVIDER, provider.name)
        putOrDelete(SettingKey.AI_MODEL, model)
    }

    override suspend fun setCompilerServer(url: String, token: String?) {
        put(SettingKey.COMPILER_URL, url)
        putOrDelete(SettingKey.COMPILER_TOKEN, token)
    }

    override suspend fun setInterviewMode(enabled: Boolean) {
        put(SettingKey.INTERVIEW_MODE, enabled.toString())
    }

    private suspend fun put(key: String, value: String) {
        settings.insertSetting(key, value)
    }

    private suspend fun putOrDelete(key: String, value: String?) {
        if (value == null) {
            settings.deleteSetting(key)
        } else {
            put(key, value)
        }
    }

    private fun Map<String, String>.toSettings(): AppSettings {
        val values = this
        val provider = values[SettingKey.AI_PROVIDER]
            ?.let { value -> runCatching { AiProvider.valueOf(value) }.getOrNull() }
            ?: AiProvider.ANTHROPIC

        return AppSettings(
            dailyGoalXp = values[SettingKey.DAILY_GOAL_XP]
                ?.toIntOrNull()
                ?.takeIf { it >= 0 }
                ?: AppSettings.DEFAULT_DAILY_GOAL_XP,
            selectedAiProvider = provider,
            selectedAiModel = values[SettingKey.AI_MODEL]?.takeIf { it.isNotEmpty() },
            compilerServerUrl = values[SettingKey.COMPILER_URL].orEmpty(),
            compilerServerToken = values[SettingKey.COMPILER_TOKEN]?.takeIf { it.isNotEmpty() },
            interviewMode = values[SettingKey.INTERVIEW_MODE].toBoolean()
        )
    }
}

private object SettingKey {
    const val DAILY_GOAL_XP = "daily_goal_xp"
    const val AI_PROVIDER = "ai_provider"
    const val AI_MODEL = "ai_model"
    const val COMPILER_URL = "compiler_server_url"
    const val COMPILER_TOKEN = "compiler_server_token"
    const val INTERVIEW_MODE = "interview_mode"
}
