package dev.agam.learn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agam.learn.ai.AiProvider
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiSettings
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.model.Exercise
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
    private val aiTutor: AiTutor? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        scope.launch {
            val goal = settingsRepository.getDailyGoal()
            val aiSettings = settingsRepository.getAiSettings()
            val compilerUrl = settingsRepository.getCompilerServerUrl()
            val compilerToken = settingsRepository.getCompilerServerToken().orEmpty()
            val interviewDefault = settingsRepository.isInterviewModeDefault()

            val provider = aiSettings?.provider ?: AiProvider.ANTHROPIC
            val defaultModel = PROVIDER_MODELS[provider]?.firstOrNull() ?: "claude-haiku-5-5"
            val model = aiSettings?.model ?: defaultModel

            _uiState.update {
                it.copy(
                    dailyGoalXp = goal,
                    aiProvider = provider,
                    aiModel = model,
                    apiKey = aiSettings?.apiKey.orEmpty(),
                    compilerServerUrl = compilerUrl,
                    compilerToken = compilerToken,
                    isInterviewModeDefault = interviewDefault
                )
            }
        }
    }

    fun onDailyGoalSelected(goalXp: Int) {
        _uiState.update { it.copy(dailyGoalXp = goalXp) }
        scope.launch {
            settingsRepository.setDailyGoal(goalXp)
        }
    }

    fun onAiProviderSelected(provider: AiProvider) {
        val defaultModel = PROVIDER_MODELS[provider]?.firstOrNull() ?: ""
        _uiState.update {
            it.copy(
                aiProvider = provider,
                aiModel = defaultModel,
                aiConnectionTestResult = null
            )
        }
    }

    fun onAiModelSelected(model: String) {
        _uiState.update { it.copy(aiModel = model, aiConnectionTestResult = null) }
    }

    fun onApiKeyChange(newKey: String) {
        _uiState.update { it.copy(apiKey = newKey, aiConnectionTestResult = null) }
    }

    fun saveAiSettings() {
        val currentState = _uiState.value
        val settings = AiSettings(
            provider = currentState.aiProvider,
            apiKey = currentState.apiKey,
            model = currentState.aiModel,
            enabled = currentState.apiKey.isNotBlank()
        )

        scope.launch {
            settingsRepository.setAiSettings(settings)
        }
    }

    fun removeApiKey() {
        _uiState.update {
            it.copy(
                apiKey = "",
                aiConnectionTestResult = null
            )
        }
        scope.launch {
            settingsRepository.clearAiKey()
        }
    }

    fun testAiConnection() {
        val currentKey = _uiState.value.apiKey
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

        _uiState.update { it.copy(isTestingAiConnection = true, aiConnectionTestResult = null) }

        scope.launch {
            val result = if (aiTutor != null) {
                val dummyExercise = Exercise.MultipleChoice(
                    id = "test-conn",
                    prompt = "Test connection",
                    options = listOf("A", "B"),
                    correctIndex = 0,
                    explanation = ""
                )
                when (val res = aiTutor.hint(dummyExercise, level = 1)) {
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
                // If no live tutor injected, perform basic validation of key presence and format
                if (currentKey.length >= 8) {
                    ConnectionTestResult(
                        isSuccess = true,
                        message = "API key syntax verified for ${_uiState.value.aiProvider.name}."
                    )
                } else {
                    ConnectionTestResult(
                        isSuccess = false,
                        message = "Invalid key length for ${_uiState.value.aiProvider.name}."
                    )
                }
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
            settingsRepository.setCompilerServerUrl(currentState.compilerServerUrl)
            settingsRepository.setCompilerServerToken(currentState.compilerToken.ifBlank { null })
        }
    }

    fun toggleInterviewModeDefault(enabled: Boolean) {
        _uiState.update { it.copy(isInterviewModeDefault = enabled) }
        scope.launch {
            settingsRepository.setInterviewModeDefault(enabled)
        }
    }
}
