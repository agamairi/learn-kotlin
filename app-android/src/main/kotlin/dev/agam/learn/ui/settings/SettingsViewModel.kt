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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** ViewModel for learner preferences, AI provider settings and compiler connection settings. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secretStore: SecretStore,
    private val aiTutor: AiTutor? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // These values describe what the tutor will read from storage. Unsaved edits cannot be tested.
    private var savedApiKey: String = ""
    private var savedProvider: AiProvider = AiProvider.ANTHROPIC
    private var savedModel: String = ""
    private var aiSettingsRevision: Long = 0
    private var modelFetchRequestId: Long = 0
    private var connectionTestRequestId: Long = 0
    private val aiSettingsSaveMutex = Mutex()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val revisionAtStart = aiSettingsRevision
        scope.launch {
            val appSettings = settingsRepository.getSettings()
            val apiKey = try {
                secretStore.get()?.trim().orEmpty()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ""
            }
            val provider = appSettings.selectedAiProvider
            val model = normalizedAiModel(provider, appSettings.selectedAiModel)
            if (revisionAtStart != aiSettingsRevision) return@launch

            savedApiKey = apiKey
            savedProvider = provider
            savedModel = model
            _uiState.update {
                it.copy(
                    dailyGoalXp = appSettings.dailyGoalXp,
                    aiProvider = provider,
                    aiModel = model,
                    apiKey = apiKey,
                    aiModelSearchQuery = "",
                    availableAiModels = emptyList(),
                    isLoadingAiModels = false,
                    aiModelCatalogMessage = null,
                    isAiModelCatalogMessageError = false,
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
        scope.launch { settingsRepository.setDailyGoalXp(goalXp) }
    }

    fun onAiProviderSelected(provider: AiProvider) {
        aiSettingsRevision++
        modelFetchRequestId++
        connectionTestRequestId++
        _uiState.update {
            it.copy(
                aiProvider = provider,
                aiModel = AiProviderConfig.defaultModel(provider),
                aiConnectionTestResult = null,
                isTestingAiConnection = false,
                isSavedMessageVisible = false,
                availableAiModels = emptyList(),
                isLoadingAiModels = false,
                aiModelCatalogMessage = null,
                isAiModelCatalogMessageError = false
            )
        }
    }

    fun onAiModelSelected(model: String) {
        aiSettingsRevision++
        modelFetchRequestId++
        connectionTestRequestId++
        _uiState.update {
            it.copy(
                aiModel = model,
                aiConnectionTestResult = null,
                isTestingAiConnection = false,
                isSavedMessageVisible = false,
                isLoadingAiModels = false,
                aiModelCatalogMessage = null,
                isAiModelCatalogMessageError = false
            )
        }
    }

    fun onAiModelSearchChanged(query: String) {
        _uiState.update { it.copy(aiModelSearchQuery = query) }
    }

    fun onApiKeyChange(newKey: String) {
        aiSettingsRevision++
        modelFetchRequestId++
        connectionTestRequestId++
        _uiState.update {
            it.copy(
                apiKey = newKey,
                aiConnectionTestResult = null,
                isTestingAiConnection = false,
                keystoreErrorMessage = null,
                isSavedMessageVisible = false,
                availableAiModels = emptyList(),
                isLoadingAiModels = false,
                aiModelCatalogMessage = null,
                isAiModelCatalogMessageError = false
            )
        }
    }

    fun saveAiSettings() {
        val stateAtStart = _uiState.value
        val revisionAtStart = aiSettingsRevision
        val keyToSave = stateAtStart.apiKey.trim()
        val modelToSave = normalizedAiModel(stateAtStart.aiProvider, stateAtStart.aiModel)

        scope.launch {
            aiSettingsSaveMutex.withLock {
                try {
                    if (keyToSave.isBlank()) secretStore.delete() else secretStore.set(keyToSave)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    if (revisionAtStart == aiSettingsRevision) {
                        _uiState.update { it.copy(keystoreErrorMessage = "Unable to save API key to secure storage") }
                    }
                    return@withLock
                }

                try {
                    settingsRepository.setAiProvider(stateAtStart.aiProvider, modelToSave)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    if (revisionAtStart == aiSettingsRevision) {
                        _uiState.update { it.copy(keystoreErrorMessage = "Unable to save AI preferences") }
                    }
                    return@withLock
                }

                savedApiKey = keyToSave
                savedProvider = stateAtStart.aiProvider
                savedModel = modelToSave
                if (revisionAtStart == aiSettingsRevision) {
                    _uiState.update {
                        it.copy(
                            apiKey = keyToSave,
                            aiModel = modelToSave,
                            isSavedMessageVisible = true,
                            keystoreErrorMessage = null,
                            aiModelCatalogMessage = null,
                            isAiModelCatalogMessageError = false
                        )
                    }
                    if (keyToSave.isNotBlank()) {
                        startModelFetch(
                            provider = stateAtStart.aiProvider,
                            key = keyToSave,
                            model = modelToSave,
                            revision = revisionAtStart
                        )
                    }
                }
            }
        }
    }

    fun removeApiKey() {
        aiSettingsRevision++
        modelFetchRequestId++
        connectionTestRequestId++
        _uiState.update {
            it.copy(
                apiKey = "",
                aiConnectionTestResult = null,
                isTestingAiConnection = false,
                keystoreErrorMessage = null,
                isSavedMessageVisible = false,
                availableAiModels = emptyList(),
                isLoadingAiModels = false,
                aiModelCatalogMessage = null,
                isAiModelCatalogMessageError = false
            )
        }
        savedApiKey = ""
        scope.launch {
            aiSettingsSaveMutex.withLock {
                try {
                    secretStore.delete()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiState.update {
                        it.copy(keystoreErrorMessage = "Unable to remove API key from secure storage")
                    }
                } finally {
                    savedApiKey = ""
                }
            }
        }
    }

    /** Refreshes the authenticated catalog using the current saved provider and key. */
    fun refreshAiModels() {
        val state = _uiState.value
        if (state.isInterviewModeDefault) {
            _uiState.update {
                it.copy(
                    aiModelCatalogMessage = "Turn off interview mode to load AI models.",
                    isAiModelCatalogMessageError = false
                )
            }
            return
        }
        val key = state.apiKey.trim()
        if (key.isBlank()) {
            _uiState.update {
                it.copy(
                    aiModelCatalogMessage = "Enter and save an API key before loading models.",
                    isAiModelCatalogMessageError = false
                )
            }
            return
        }
        val provider = state.aiProvider
        val model = normalizedAiModel(provider, state.aiModel)
        if (key != savedApiKey || provider != savedProvider || model != savedModel) {
            _uiState.update {
                it.copy(
                    aiModelCatalogMessage = "Save AI settings first, then load available models.",
                    isAiModelCatalogMessageError = false
                )
            }
            return
        }
        if (aiTutor == null) {
            _uiState.update {
                it.copy(
                    aiModelCatalogMessage = "AI model discovery is not available in this build.",
                    isAiModelCatalogMessageError = false
                )
            }
            return
        }
        startModelFetch(provider, key, model, aiSettingsRevision)
    }

    private fun startModelFetch(provider: AiProvider, key: String, model: String, revision: Long) {
        val tutor = aiTutor ?: return
        if (revision != aiSettingsRevision) return
        if (_uiState.value.isInterviewModeDefault) {
            _uiState.update {
                it.copy(
                    isLoadingAiModels = false,
                    aiModelCatalogMessage = "Turn off interview mode to load AI models.",
                    isAiModelCatalogMessageError = false
                )
            }
            return
        }
        val requestId = ++modelFetchRequestId
        _uiState.update {
            it.copy(
                isLoadingAiModels = true,
                aiModelCatalogMessage = null,
                isAiModelCatalogMessageError = false
            )
        }

        scope.launch {
            val result = try {
                tutor.listModels()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AiResult.Error("AI provider request failed")
            }
            val current = _uiState.value
            if (requestId != modelFetchRequestId || revision != aiSettingsRevision ||
                provider != current.aiProvider || key != current.apiKey.trim() ||
                model != normalizedAiModel(current.aiProvider, current.aiModel) ||
                provider != savedProvider || key != savedApiKey || model != savedModel
            ) return@launch

            when (result) {
                is AiResult.Success -> {
                    val models = result.data.map(String::trim).filter(String::isNotEmpty).distinct()
                    _uiState.update {
                        it.copy(
                            availableAiModels = models,
                            isLoadingAiModels = false,
                            aiModelCatalogMessage = if (models.isEmpty()) {
                                "No text generation models were returned. You can still enter a model ID."
                            } else null,
                            isAiModelCatalogMessageError = false
                        )
                    }
                }
                is AiResult.Error -> _uiState.update {
                    it.copy(
                        isLoadingAiModels = false,
                        aiModelCatalogMessage = "Couldn't load models: ${result.message}",
                        isAiModelCatalogMessageError = true
                    )
                }
            }
        }
    }

    fun testAiConnection() {
        val state = _uiState.value
        val currentKey = state.apiKey.trim()
        if (state.isInterviewModeDefault) {
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

        val provider = state.aiProvider
        val model = normalizedAiModel(provider, state.aiModel)
        if (currentKey != savedApiKey || provider != savedProvider || model != savedModel) {
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

        val revision = aiSettingsRevision
        val requestId = ++connectionTestRequestId
        _uiState.update { it.copy(isTestingAiConnection = true, aiConnectionTestResult = null) }

        scope.launch {
            val result = if (aiTutor != null) {
                when (val response = try {
                    aiTutor.testConnection()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    AiResult.Error("AI provider request failed")
                }) {
                    is AiResult.Success -> ConnectionTestResult(
                        isSuccess = true,
                        message = "Connection verified successfully with ${provider.name}."
                    )
                    is AiResult.Error -> ConnectionTestResult(
                        isSuccess = false,
                        message = response.message
                    )
                }
            } else {
                ConnectionTestResult(
                    isSuccess = false,
                    message = "AI tutor is not available in this build."
                )
            }

            val current = _uiState.value
            if (requestId == connectionTestRequestId && revision == aiSettingsRevision &&
                provider == current.aiProvider && model == normalizedAiModel(current.aiProvider, current.aiModel) &&
                currentKey == current.apiKey.trim() && provider == savedProvider && model == savedModel &&
                currentKey == savedApiKey
            ) {
                _uiState.update {
                    it.copy(isTestingAiConnection = false, aiConnectionTestResult = result)
                }
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
        val state = _uiState.value
        scope.launch {
            settingsRepository.setCompilerServer(
                url = state.compilerServerUrl,
                token = state.compilerToken.ifBlank { null }
            )
        }
    }

    fun toggleInterviewModeDefault(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isInterviewModeDefault = enabled,
                isLoadingAiModels = if (enabled) false else it.isLoadingAiModels,
                isTestingAiConnection = if (enabled) false else it.isTestingAiConnection,
                aiConnectionTestResult = if (enabled) null else it.aiConnectionTestResult
            )
        }
        if (enabled) {
            modelFetchRequestId++
            connectionTestRequestId++
        }
        scope.launch { settingsRepository.setInterviewMode(enabled) }
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
        AiProviderConfig.normalizeModel(provider, model)

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                    settingsRepository = appContainer.settingsRepository,
                    secretStore = appContainer.secretStore,
                    aiTutor = appContainer.aiTutor
                ) as T
            }
    }
}
