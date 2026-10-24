package dev.agam.learn.ui.settings

import dev.agam.learn.ai.AiProvider
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.model.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private lateinit var fakeRepo: InMemorySettingsRepository
    private lateinit var fakeAiTutor: FakeAiTutor
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        fakeRepo = InMemorySettingsRepository(
            initialGoal = 50,
            initialServerUrl = "http://10.0.2.2:8080",
            initialInterviewDefault = false
        )
        fakeAiTutor = FakeAiTutor()
    }

    @Test
    fun testInitialLoadFromRepository() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
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
            coroutineScope = testScope
        )

        viewModel.onDailyGoalSelected(75)

        assertEquals(75, viewModel.uiState.value.dailyGoalXp)

        val checkScope = CoroutineScope(Dispatchers.Unconfined)
        var persistedGoal = 0
        kotlinx.coroutines.runBlocking {
            persistedGoal = fakeRepo.getDailyGoal()
        }
        assertEquals(75, persistedGoal)
    }

    @Test
    fun testAiProviderAndModelSelection() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            coroutineScope = testScope
        )

        // Select OpenAI
        viewModel.onAiProviderSelected(AiProvider.OPENAI)
        assertEquals(AiProvider.OPENAI, viewModel.uiState.value.aiProvider)
        assertEquals("gpt-6-luna", viewModel.uiState.value.aiModel)

        // Select Gemini
        viewModel.onAiProviderSelected(AiProvider.GEMINI)
        assertEquals(AiProvider.GEMINI, viewModel.uiState.value.aiProvider)
        assertEquals("gemini-3.5-flash-lite", viewModel.uiState.value.aiModel)

        // Custom model selection
        viewModel.onAiModelSelected("gemini-4-argon")
        assertEquals("gemini-4-argon", viewModel.uiState.value.aiModel)
    }

    @Test
    fun testApiKeySaveAndRemoveFlow() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            coroutineScope = testScope
        )

        // Enter key
        viewModel.onApiKeyChange("sk-ant-api03-secret-key-12345")
        assertEquals("sk-ant-api03-secret-key-12345", viewModel.uiState.value.apiKey)

        // Save AI settings
        viewModel.saveAiSettings()

        var savedSettings = kotlinx.coroutines.runBlocking { fakeRepo.getAiSettings() }
        assertNotNull(savedSettings)
        assertEquals("sk-ant-api03-secret-key-12345", savedSettings?.apiKey)
        assertTrue(savedSettings!!.enabled)

        // Remove key flow
        viewModel.removeApiKey()
        assertEquals("", viewModel.uiState.value.apiKey)

        savedSettings = kotlinx.coroutines.runBlocking { fakeRepo.getAiSettings() }
        assertEquals("", savedSettings?.apiKey)
        assertFalse(savedSettings!!.enabled)
    }

    @Test
    fun testCompilerServerSettingsSave() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            coroutineScope = testScope
        )

        viewModel.onCompilerUrlChange("https://compiler.internal.lan")
        viewModel.onCompilerTokenChange("bearer-token-abc")
        viewModel.saveCompilerSettings()

        val url = kotlinx.coroutines.runBlocking { fakeRepo.getCompilerServerUrl() }
        val token = kotlinx.coroutines.runBlocking { fakeRepo.getCompilerServerToken() }

        assertEquals("https://compiler.internal.lan", url)
        assertEquals("bearer-token-abc", token)
    }

    @Test
    fun testInterviewModeDefaultToggle() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            coroutineScope = testScope
        )

        viewModel.toggleInterviewModeDefault(true)
        assertTrue(viewModel.uiState.value.isInterviewModeDefault)

        val persisted = kotlinx.coroutines.runBlocking { fakeRepo.isInterviewModeDefault() }
        assertTrue(persisted)
    }

    @Test
    fun testConnectionTestWithEmptyKeyFails() {
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
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
        fakeAiTutor.hintResult = AiResult.Success("OK")
        val viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            aiTutor = fakeAiTutor,
            coroutineScope = testScope
        )

        viewModel.onApiKeyChange("sk-ant-test-key-long-enough")
        viewModel.testAiConnection()

        val result = viewModel.uiState.value.aiConnectionTestResult
        assertNotNull(result)
        assertTrue(result!!.isSuccess)
    }

    private class FakeAiTutor : AiTutor {
        var hintResult: AiResult<String> = AiResult.Success("OK")

        override suspend fun explainMistake(
            exercise: Exercise,
            userAnswer: String,
            expectedResult: String
        ): AiResult<String> = AiResult.Success("OK")

        override suspend fun hint(exercise: Exercise, level: Int): AiResult<String> = hintResult

        override suspend fun generateCards(topic: String, count: Int): AiResult<List<Exercise>> =
            AiResult.Success(emptyList())

        override suspend fun reviewCode(exercise: Exercise, code: String): AiResult<String> =
            AiResult.Success("OK")
    }
}
