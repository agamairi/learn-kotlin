package dev.agam.learn.fake

import dev.agam.learn.ai.AiProvider
import dev.agam.learn.data.AppSettings
import dev.agam.learn.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsRepository] for unit tests. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override suspend fun getSettings(): AppSettings = state.value

    override fun observeSettings(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun setDailyGoalXp(xp: Int) {
        state.update { it.copy(dailyGoalXp = xp) }
    }

    override suspend fun setAiProvider(provider: AiProvider, model: String?) {
        state.update { it.copy(selectedAiProvider = provider, selectedAiModel = model) }
    }

    override suspend fun setCompilerServer(url: String, token: String?) {
        state.update { it.copy(compilerServerUrl = url, compilerServerToken = token) }
    }

    override suspend fun setInterviewMode(enabled: Boolean) {
        state.update { it.copy(interviewMode = enabled) }
    }

    override suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        state.update { it.copy(soundEffectsEnabled = enabled) }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        state.update { it.copy(hapticsEnabled = enabled) }
    }

    override suspend fun setReducedMotionEnabled(enabled: Boolean) {
        state.update { it.copy(reducedMotionEnabled = enabled) }
    }
}
