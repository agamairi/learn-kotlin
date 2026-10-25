package dev.agam.learn.ui.settings

import dev.agam.learn.ai.AiProvider
import dev.agam.learn.ai.AiProviderConfig
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.data.AppSettings
import dev.agam.learn.data.SecretStore
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.model.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private lateinit var fakeRepo: FakeSharedSettingsRepository
    private lateinit var fakeSecretStore: FakeSecretStore
    private lateinit var fakeAiTutor: FakeAiTutor
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        fakeRepo = FakeSharedSettingsRepository(
            initialSettings = AppSettings(
                dailyGoalXp = 50,
                compilerServerUrl = "http://10.0.2.2:8080",
                interviewMode = false
            )
        )
        fakeSecretStore = FakeSecretStore()
        fakeAiTutor = FakeAiTutor()
    }

    @Test
    fun testInitialLoadFromRepository() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            aiTutor = fakeAiTutor,
            coroutineScope = testScope
        )

        val state = viewModel.uiState.value
        assertEquals(50, state.dailyGoalXp)
        assertEquals("http://10.0.2.2:8080", state.compilerServerUrl)
        assertFalse(state.isInterviewModeDefault)
        assertEquals("", state.apiKey)
    }

    @Test
    fun testDailyGoalSelectionAndPersistence() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )

        viewModel.onDailyGoalSelected(75)

        assertEquals(75, viewModel.uiState.value.dailyGoalXp)

        val persistedGoal = runBlocking { fakeRepo.getSettings().dailyGoalXp }
        assertEquals(75, persistedGoal)
    }

    @Test
    fun testAiProviderAndModelSelection() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )

        // Select OpenAI
        viewModel.onAiProviderSelected(AiProvider.OPENAI)
        assertEquals(AiProvider.OPENAI, viewModel.uiState.value.aiProvider)
        assertEquals(AiProviderConfig.OPENAI_DEFAULT_MODEL, viewModel.uiState.value.aiModel)

        // Select Gemini
        viewModel.onAiProviderSelected(AiProvider.GEMINI)
        assertEquals(AiProvider.GEMINI, viewModel.uiState.value.aiProvider)
        assertEquals(AiProviderConfig.GEMINI_DEFAULT_MODEL, viewModel.uiState.value.aiModel)

        // Custom model selection
        viewModel.onAiModelSelected("gemini-4-argon")
        assertEquals("gemini-4-argon", viewModel.uiState.value.aiModel)

        // Existing providers keep their defaults, and OpenRouter selects its own default.
        viewModel.onAiProviderSelected(AiProvider.OPENROUTER)
        assertEquals(AiProvider.OPENROUTER, viewModel.uiState.value.aiProvider)
        assertEquals(AiProviderConfig.OPENROUTER_DEFAULT_MODEL, viewModel.uiState.value.aiModel)
    }

    @Test
    fun openRouterCustomModelIsTrimmedAndReloadedFromSavedSettings() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )
        viewModel.onAiProviderSelected(AiProvider.OPENROUTER)
        viewModel.onAiModelSelected("  openai/gpt-4o-mini  ")

        viewModel.saveAiSettings()

        assertEquals("openai/gpt-4o-mini", viewModel.uiState.value.aiModel)
        assertEquals("openai/gpt-4o-mini", runBlocking { fakeRepo.getSettings().selectedAiModel })

        val reloadedViewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )
        assertEquals(AiProvider.OPENROUTER, reloadedViewModel.uiState.value.aiProvider)
        assertEquals("openai/gpt-4o-mini", reloadedViewModel.uiState.value.aiModel)
    }

    @Test
    fun blankOpenRouterModelFallsBackToDefaultInUiAndPersistence() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )
        viewModel.onAiProviderSelected(AiProvider.OPENROUTER)
        viewModel.onAiModelSelected("   ")

        viewModel.saveAiSettings()

        assertEquals(AiProviderConfig.OPENROUTER_DEFAULT_MODEL, viewModel.uiState.value.aiModel)
        assertEquals(
            AiProviderConfig.OPENROUTER_DEFAULT_MODEL,
            runBlocking { fakeRepo.getSettings().selectedAiModel }
        )
    }

    @Test
    fun unsavedOpenRouterCustomModelCannotBeConnectionTested() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            aiTutor = fakeAiTutor,
            coroutineScope = testScope
        )
        viewModel.onAiProviderSelected(AiProvider.OPENROUTER)
        viewModel.onApiKeyChange("sk-or-test-key-long-enough")
        viewModel.saveAiSettings()
        viewModel.onAiModelSelected("openai/gpt-4o-mini")

        viewModel.testAiConnection()

        val result = viewModel.uiState.value.aiConnectionTestResult
        assertNotNull(result)
        assertFalse(result!!.isSuccess)
        assertEquals("Save your AI settings first, then test the connection.", result.message)
        assertEquals(0, fakeAiTutor.testConnectionCalls)
    }

    @Test
    fun testApiKeySaveAndRemoveFlow() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )

        // Enter key
        viewModel.onApiKeyChange("sk-ant-api03-secret-key-12345")
        assertEquals("sk-ant-api03-secret-key-12345", viewModel.uiState.value.apiKey)

        // Save AI settings
        viewModel.saveAiSettings()

        val savedKey = runBlocking { fakeSecretStore.get() }
        assertEquals("sk-ant-api03-secret-key-12345", savedKey)

        // Remove key flow
        viewModel.removeApiKey()
        assertEquals("", viewModel.uiState.value.apiKey)

        val afterRemoveKey = runBlocking { fakeSecretStore.get() }
        assertNull(afterRemoveKey)
    }

    @Test
    fun testCompilerServerSettingsSave() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )

        viewModel.onCompilerUrlChange("https://compiler.internal.lan")
        viewModel.onCompilerTokenChange("bearer-token-abc")
        viewModel.saveCompilerSettings()

        val settings = runBlocking { fakeRepo.getSettings() }

        assertEquals("https://compiler.internal.lan", settings.compilerServerUrl)
        assertEquals("bearer-token-abc", settings.compilerServerToken)
    }

    @Test
    fun testInterviewModeDefaultToggle() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )

        viewModel.toggleInterviewModeDefault(true)
        assertTrue(viewModel.uiState.value.isInterviewModeDefault)

        val persisted = runBlocking { fakeRepo.getSettings().interviewMode }
        assertTrue(persisted)
    }

    @Test
    fun experiencePreferencesPersistIndependentlyAndReload() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        )

        viewModel.setSoundEffectsEnabled(false)
        viewModel.setReducedMotionEnabled(true)
        viewModel.setHapticsEnabled(false)

        val saved = runBlocking { fakeRepo.getSettings() }
        assertFalse(saved.soundEffectsEnabled)
        assertFalse(saved.hapticsEnabled)
        assertTrue(saved.reducedMotionEnabled)
        assertFalse(saved.interviewMode)

        val reloaded = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            coroutineScope = testScope
        ).uiState.value
        assertFalse(reloaded.soundEffectsEnabled)
        assertFalse(reloaded.hapticsEnabled)
        assertTrue(reloaded.reducedMotionEnabled)
    }

    @Test
    fun testConnectionTestWithEmptyKeyFails() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            aiTutor = fakeAiTutor,
            coroutineScope = testScope
        )

        viewModel.onApiKeyChange("")
        viewModel.testAiConnection()

        val result = viewModel.uiState.value.aiConnectionTestResult
        assertNotNull(result)
        assertFalse(result!!.isSuccess)
    }

    @Test
    fun testConnectionTestWithValidKeySucceeds() {
        fakeAiTutor.testConnectionResult = AiResult.Success(Unit)
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            aiTutor = fakeAiTutor,
            coroutineScope = testScope
        )

        viewModel.onApiKeyChange("sk-ant-test-key-long-enough")
        viewModel.saveAiSettings()
        viewModel.testAiConnection()

        val result = viewModel.uiState.value.aiConnectionTestResult
        assertNotNull(result)
        assertTrue(result!!.isSuccess)
        assertEquals(1, fakeAiTutor.testConnectionCalls)
        assertEquals(0, fakeAiTutor.hintCalls)
    }

    @Test
    fun testConnectionTestWithProviderErrorFails() {
        fakeAiTutor.testConnectionResult = AiResult.Error("Invalid API key")
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            aiTutor = fakeAiTutor,
            coroutineScope = testScope
        )

        viewModel.onApiKeyChange("sk-ant-test-key-long-enough")
        viewModel.saveAiSettings()
        viewModel.testAiConnection()

        val result = viewModel.uiState.value.aiConnectionTestResult
        assertNotNull(result)
        assertFalse(result!!.isSuccess)
        assertTrue(result.message.contains("Invalid API key"))
        assertEquals(1, fakeAiTutor.testConnectionCalls)
        assertEquals(0, fakeAiTutor.hintCalls)
    }

    @Test
    fun testConnectionOnlyChecksSavedSettings() {
        fakeAiTutor.testConnectionResult = AiResult.Success(Unit)
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            aiTutor = fakeAiTutor,
            coroutineScope = testScope
        )

        // A key typed but not saved must not be reported as a working connection.
        viewModel.onApiKeyChange("sk-ant-unsaved-key-value")
        viewModel.testAiConnection()

        val result = viewModel.uiState.value.aiConnectionTestResult
        assertNotNull(result)
        assertFalse(result!!.isSuccess)
        assertEquals(0, fakeAiTutor.testConnectionCalls)
        assertEquals(0, fakeAiTutor.hintCalls)
    }

    @Test
    fun testConnectionIsRefusedInInterviewModeWithoutCallingTheTutor() {
        fakeRepo = FakeSharedSettingsRepository(AppSettings(interviewMode = true))
        fakeAiTutor.testConnectionResult = AiResult.Success(Unit)
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = fakeSecretStore,
            aiTutor = fakeAiTutor,
            coroutineScope = testScope
        )

        viewModel.onApiKeyChange("sk-ant-test-key-long-enough")
        viewModel.testAiConnection()

        val result = viewModel.uiState.value.aiConnectionTestResult
        assertNotNull(result)
        assertFalse(result!!.isSuccess)
        assertEquals(0, fakeAiTutor.testConnectionCalls)
        assertEquals(0, fakeAiTutor.hintCalls)
    }

    @Test
    fun keystoreExceptionOnSaveBecomesFriendlyErrorMessageRatherThanCrashing() {
        val failingStore = object : SecretStore {
            override suspend fun get(): String? = null
            override suspend fun set(value: String) {
                throw java.security.KeyStoreException("Keystore hardware unavailable")
            }
            override suspend fun delete() {}
        }
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = failingStore,
            coroutineScope = testScope
        )

        viewModel.onApiKeyChange("sk-ant-test-key")
        viewModel.saveAiSettings()

        val state = viewModel.uiState.value
        assertNotNull(state.keystoreErrorMessage)
        assertTrue(state.keystoreErrorMessage!!.contains("Unable to save API key to secure storage"))
    }

    @Test
    fun keystoreExceptionOnRemoveBecomesFriendlyErrorMessageRatherThanCrashing() {
        val failingStore = object : SecretStore {
            override suspend fun get(): String? = "existing-key"
            override suspend fun set(value: String) {}
            override suspend fun delete() {
                throw java.security.KeyStoreException("Keystore failed to delete alias")
            }
        }
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            secretStore = failingStore,
            coroutineScope = testScope
        )

        viewModel.removeApiKey()

        val state = viewModel.uiState.value
        assertNotNull(state.keystoreErrorMessage)
        assertTrue(state.keystoreErrorMessage!!.contains("Unable to remove API key from secure storage"))
    }

    private class FakeAiTutor : AiTutor {
        var testConnectionResult: AiResult<Unit> = AiResult.Success(Unit)
        var testConnectionCalls = 0
        var hintResult: AiResult<String> = AiResult.Success("OK")
        var hintCalls = 0

        override suspend fun testConnection(): AiResult<Unit> {
            testConnectionCalls++
            return testConnectionResult
        }

        override suspend fun explainMistake(
            exercise: Exercise,
            userAnswer: String,
            expectedResult: String
        ): AiResult<String> = AiResult.Success("OK")

        override suspend fun hint(exercise: Exercise, level: Int): AiResult<String> {
            hintCalls++
            return hintResult
        }

        override suspend fun generateCards(topic: String, count: Int): AiResult<List<Exercise>> =
            AiResult.Success(emptyList())

        override suspend fun reviewCode(exercise: Exercise, code: String): AiResult<String> =
            AiResult.Success("OK")
    }

    private class FakeSharedSettingsRepository(
        initialSettings: AppSettings = AppSettings()
    ) : SettingsRepository {
        private val state = MutableStateFlow(initialSettings)

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

    private class FakeSecretStore(private var key: String? = null) : SecretStore {
        override suspend fun get(): String? = key
        override suspend fun set(value: String) { key = value }
        override suspend fun delete() { key = null }
    }
}
