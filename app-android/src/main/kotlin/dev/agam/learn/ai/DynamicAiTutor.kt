package dev.agam.learn.ai

import dev.agam.learn.data.SecretStore
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.model.Exercise
import io.ktor.client.HttpClient

/**
 * [AiTutor] that delegates to [AiTutorImpl], configured with the provider and model chosen in
 * [SettingsRepository]. The API key is read from [SecretStore] on every request, and one
 * [HttpClient] is shared by all requests so connections are reused rather than leaked.
 */
class DynamicAiTutor(
    private val settingsRepository: SettingsRepository,
    private val secretStore: SecretStore,
    private val httpClient: HttpClient = createAiHttpClient(),
    private val sessionInterviewMode: (() -> Boolean)? = null
) : AiTutor {

    fun forSession(sessionMode: () -> Boolean): DynamicAiTutor =
        DynamicAiTutor(
            settingsRepository = settingsRepository,
            secretStore = secretStore,
            httpClient = httpClient,
            sessionInterviewMode = sessionMode
        )

    suspend fun createTutorImpl(): AiTutorImpl {
        val appSettings = settingsRepository.getSettings()
        val effectiveInterviewMode = sessionInterviewMode?.invoke() ?: appSettings.interviewMode
        val aiSettings = AiSettings(
            provider = appSettings.selectedAiProvider,
            model = appSettings.selectedAiModel,
            enabled = true,
            interviewMode = effectiveInterviewMode
        )
        return AiTutorImpl(
            settings = aiSettings,
            secretStore = secretStore,
            httpClient = httpClient
        )
    }

    suspend fun testConnection(): AiResult<Unit> =
        createTutorImpl().testConnection()

    override suspend fun explainMistake(
        exercise: Exercise,
        userAnswer: String,
        expectedResult: String
    ): AiResult<String> = createTutorImpl().explainMistake(exercise, userAnswer, expectedResult)

    override suspend fun hint(
        exercise: Exercise,
        level: Int
    ): AiResult<String> = createTutorImpl().hint(exercise, level)

    override suspend fun generateCards(
        topic: String,
        count: Int
    ): AiResult<List<Exercise>> = createTutorImpl().generateCards(topic, count)

    override suspend fun reviewCode(
        exercise: Exercise,
        code: String
    ): AiResult<String> = createTutorImpl().reviewCode(exercise, code)
}
