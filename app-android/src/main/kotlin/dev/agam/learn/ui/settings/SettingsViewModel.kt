package dev.agam.learn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.ai.AiProvider
import dev.agam.learn.ai.AiProviderConfig
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.data.SecretStore
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing learner settings, AI tutor configuration, key lifecycle,
 * and compiler runner connection settings.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secretStore: SecretStore,
    private val aiTutor: AiTutor? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // The AI settings the tutor actually uses (saved values). The connection test checks these,
    // so an unsaved key or provider is never reported as working.
    private var savedApiKey: String = ""
    private var savedProvider: AiProvider = AiProvider.ANTHROPIC
    private var savedModel: String = ""

    init {
        loadSettings()
    }

    fun loadSettings() {
        scope.launch {
            val appSettings = settingsRepository.getSettings()
            // A failing Keystore must not take the whole settings screen down; it shows as "no key saved".
            val apiKey = runCatching { secretStore.get() }.getOrNull().orEmpty()
            val provider = appSettings.selectedAiProvider
            // Same default the tutor uses when no model is saved, so the screen never shows a different model.
            val model = normalizedAiModel(provider, appSettings.selectedAiModel)
            if (provider == AiProvider.OPENROUTER && appSettings.selectedAiModel != model) {
                settingsRepository.setAiProvider(provider, model)
            }
            savedApiKey = apiKey
            savedProvider = provider
            savedModel = model

            _uiState.update {
                it.copy(
                    dailyGoalXp = appSettings.dailyGoalXp,
                    aiProvider = provider,
                    aiModel = model,
                    apiKey = apiKey,
                    compilerServerUrl = appSettings.compilerServerUrl.ifBlank { "http://10.0.2.2:8080" },
                    compilerToken = appSettings.compilerServerToken.orEmpty(),
                    isInterviewModeDefault = appSettings.interviewMode,
                    soundEffectsEnabled = appSettings.soundEffectsEnabled,
                    hapticsEnabled = appSettings.hapticsEnabled,
                    reducedMotionEnabled = appSettings.reducedMotionEnabled
                )
            }
        }
    }

    fun onDailyGoalSelected(goalXp: Int) {
        _uiState.update { it.copy(dailyGoalXp = goalXp) }
        scope.launch {
            settingsRepository.setDailyGoalXp(goalXp)
        }
    }

    fun onAiProviderSelected(provider: AiProvider) {
        _uiState.update {
            it.copy(
                aiProvider = provider,
                aiModel = AiProviderConfig.defaultModel(provider),
                aiConnectionTestResult = null
            )
        }
    }

    fun onAiModelSelected(model: String) {
        _uiState.update { it.copy(aiModel = model, aiConnectionTestResult = null) }
    }

    fun onApiKeyChange(newKey: String) {
        _uiState.update { it.copy(apiKey = newKey, aiConnectionTestResult = null, keystoreErrorMessage = null) }
    }

    fun saveAiSettings() {
        val currentState = _uiState.value
        val modelToSave = normalizedAiModel(currentState.aiProvider, currentState.aiModel)
        val persistedModel = if (currentState.aiProvider == AiProvider.OPENROUTER) {
            modelToSave
        } else {
            currentState.aiModel.ifBlank { null }
        }
        scope.launch {
            try {
                if (currentState.apiKey.isBlank()) {
                    secretStore.delete()
                } else {
                    secretStore.set(currentState.apiKey)
                }
                settingsRepository.setAiProvider(
                    currentState.aiProvider,
                    persistedModel
                )
                savedApiKey = currentState.apiKey
                savedProvider = currentState.aiProvider
                savedModel = modelToSave
                _uiState.update {
                    it.copy(
                        aiModel = if (it.aiProvider == currentState.aiProvider &&
                            it.aiModel == currentState.aiModel
                        ) modelToSave else it.aiModel,
                        isSavedMessageVisible = true,
                        keystoreErrorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        keystoreErrorMessage = "Unable to save API key to secure storage: ${e.message ?: "Keystore error"}"
                    )
                }
            }
        }
    }

    fun removeApiKey() {
        _uiState.update {
            it.copy(
                apiKey = "",
                aiConnectionTestResult = null,
                keystoreErrorMessage = null
            )
        }
        savedApiKey = ""
        scope.launch {
            try {
                secretStore.delete()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        keystoreErrorMessage = "Unable to remove API key from secure storage: ${e.message ?: "Keystore error"}"
                    )
                }
            }
        }
    }

    fun testAiConnection() {
        val currentKey = _uiState.value.apiKey
        if (_uiState.value.isInterviewModeDefault) {
            // The tutor refuses in interview mode, so do not send a request that can only fail.
            _uiState.update {
                it.copy(
                    aiConnectionTestResult = ConnectionTestResult(
                        isSuccess = false,
                        message = "Turn off interview mode to test the AI connection."
                    )
                )
            }
            return
        }
        if (currentKey.isBlank()) {
            _uiState.update {
                it.copy(
                    aiConnectionTestResult = ConnectionTestResult(
                        isSuccess = false,
                        message = "Please enter an API key before testing."
                    )
                )
            }
            return
        }

        val state = _uiState.value
        val isSaved = currentKey == savedApiKey &&
            state.aiProvider == savedProvider &&
            normalizedAiModel(state.aiProvider, state.aiModel) == savedModel
        if (!isSaved) {
            _uiState.update {
                it.copy(
                    aiConnectionTestResult = ConnectionTestResult(
                        isSuccess = false,
                        message = "Save your AI settings first, then test the connection."
                    )
                )
            }
            return
        }

        _uiState.update { it.copy(isTestingAiConnection = true, aiConnectionTestResult = null) }

        scope.launch {
            val result = if (aiTutor != null) {
                when (val res = aiTutor.testConnection()) {
                    is AiResult.Success -> ConnectionTestResult(
                        isSuccess = true,
                        message = "Connection verified successfully with ${_uiState.value.aiProvider.name}."
                    )
                    is AiResult.Error -> ConnectionTestResult(
                        isSuccess = false,
                        message = res.message + (res.cause?.let { " ($it)" } ?: "")
                    )
                }
            } else {
                // Never report success without contacting the provider.
                ConnectionTestResult(
                    isSuccess = false,
                    message = "AI tutor is not available in this build."
                )
            }

            _uiState.update {
                it.copy(
                    isTestingAiConnection = false,
                    aiConnectionTestResult = result
                )
            }
        }
    }

    fun onCompilerUrlChange(url: String) {
        _uiState.update { it.copy(compilerServerUrl = url) }
    }

    fun onCompilerTokenChange(token: String) {
        _uiState.update { it.copy(compilerToken = token) }
    }

    fun saveCompilerSettings() {
        val currentState = _uiState.value
        scope.launch {
            settingsRepository.setCompilerServer(
                url = currentState.compilerServerUrl,
                token = currentState.compilerToken.ifBlank { null }
            )
        }
    }

    fun toggleInterviewModeDefault(enabled: Boolean) {
        _uiState.update { it.copy(isInterviewModeDefault = enabled) }
        scope.launch {
            settingsRepository.setInterviewMode(enabled)
        }
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(soundEffectsEnabled = enabled) }
        scope.launch { settingsRepository.setSoundEffectsEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hapticsEnabled = enabled) }
        scope.launch { settingsRepository.setHapticsEnabled(enabled) }
    }

    fun setReducedMotionEnabled(enabled: Boolean) {
        _uiState.update { it.copy(reducedMotionEnabled = enabled) }
        scope.launch { settingsRepository.setReducedMotionEnabled(enabled) }
    }

    private fun normalizedAiModel(provider: AiProvider, model: String?): String =
        if (provider == AiProvider.OPENROUTER) {
            model?.trim()?.takeIf { it.isNotEmpty() } ?: AiProviderConfig.OPENROUTER_DEFAULT_MODEL
        } else {
            model ?: AiProviderConfig.defaultModel(provider)
        }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        settingsRepository = appContainer.settingsRepository,
                        secretStore = appContainer.secretStore,
                        aiTutor = appContainer.aiTutor
                    ) as T
                }
            }
    }
}
