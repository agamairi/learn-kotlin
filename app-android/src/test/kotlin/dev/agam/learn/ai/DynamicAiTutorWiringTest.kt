package dev.agam.learn.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.agam.learn.data.AppSettings
import dev.agam.learn.data.AndroidSecretStore
import dev.agam.learn.data.LearnDatabase
import dev.agam.learn.data.SecretStore
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.data.SqlDelightSettingsRepository
import dev.agam.learn.fake.FakeSettingsRepository
import dev.agam.learn.model.Exercise
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the wiring DefaultAppContainer uses: [DynamicAiTutor] reads the provider from settings,
 * the key from the secret store, and sends nothing when there is no key or interview mode is on.
 */
class DynamicAiTutorWiringTest {

    private val exercise = Exercise.MultipleChoice(
        id = "q1",
        prompt = "Which keyword is read-only?",
        options = listOf("val", "var"),
        correctIndex = 0,
        explanation = "val is read-only."
    )

    private class FixedSecretStore(
        private var value: String? = null,
        private val failing: Boolean = false
    ) : SecretStore {
        override suspend fun get(): String? {
            if (failing) error("keystore unavailable")
            return value
        }

        override suspend fun set(value: String) {
            this.value = value
        }

        override suspend fun delete() {
            value = null
        }
    }

    /** Builds the tutor the same way DefaultAppContainer does, with a mock engine recording requests. */
    private fun wiredTutor(
        settingsRepository: SettingsRepository,
        secretStore: SecretStore,
        requests: MutableList<HttpRequestData>,
        responseBody: String = "{}"
    ): DynamicAiTutor {
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler { request ->
                requests += request
                respond(
                    content = ByteReadChannel(responseBody),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        })
        return DynamicAiTutor(
            settingsRepository = settingsRepository,
            secretStore = secretStore,
            httpClient = createAiHttpClient(engine)
        )
    }

    private fun wiredTutor(
        settings: AppSettings,
        secretStore: SecretStore,
        requests: MutableList<HttpRequestData>,
        responseBody: String = "{}"
    ): DynamicAiTutor = wiredTutor(FakeSettingsRepository(settings), secretStore, requests, responseBody)

    @Test
    fun providerChosenInSettingsDecidesTheEndpointAndKeyHeader() = runBlocking {
        val key = "test-key-123"
        val expected = mapOf(
            AiProvider.ANTHROPIC to "api.anthropic.com",
            AiProvider.OPENAI to "api.openai.com",
            AiProvider.GEMINI to "generativelanguage.googleapis.com"
        )

        for ((provider, host) in expected) {
            val requests = mutableListOf<HttpRequestData>()
            val tutor = wiredTutor(
                settings = AppSettings(selectedAiProvider = provider),
                secretStore = FixedSecretStore(key),
                requests = requests
            )

            tutor.hint(exercise, level = 1)

            assertEquals("$provider makes one request", 1, requests.size)
            val request = requests.single()
            assertEquals("$provider endpoint", host, request.url.host)
            assertFalse("$provider must not put the key in the URL", request.url.toString().contains(key))
            when (provider) {
                AiProvider.GEMINI -> assertEquals(key, request.headers["x-goog-api-key"])
                else -> assertEquals("Bearer $key", request.headers["Authorization"])
            }
        }
    }

    @Test
    fun modelChosenInSettingsIsUsedByTheSelectedProvider() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val tutor = wiredTutor(
            settings = AppSettings(selectedAiProvider = AiProvider.GEMINI, selectedAiModel = "gemini-4-argon"),
            secretStore = FixedSecretStore("test-key-123"),
            requests = requests
        )

        tutor.hint(exercise, level = 1)

        assertTrue(requests.single().url.encodedPath.contains("gemini-4-argon"))
    }

    @Test
    fun savedOpenRouterSettingsAndKeyWireChatAndConnectionAndRemainInterviewBlocked() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            LearnDatabase.Schema.create(driver)
            val database = LearnDatabase(driver)
            val settings = SqlDelightSettingsRepository(database)
            val model = "anthropic/claude-sonnet-4.5"
            val key = "saved-openrouter-key"
            settings.setAiProvider(AiProvider.OPENROUTER, model)
            val encryptionKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val secretStore = AndroidSecretStore(database, keyProvider = { encryptionKey })
            secretStore.set(key)
            val requests = mutableListOf<HttpRequestData>()
            val tutor = wiredTutor(
                settings,
                secretStore,
                requests,
                responseBody = """{"choices":[{"message":{"role":"assistant","content":"A hint."}}]}"""
            )

            assertEquals(AiResult.Success("A hint."), tutor.hint(exercise, level = 1))
            val chatRequest = requests.single()
            assertEquals(HttpMethod.Post, chatRequest.method)
            assertEquals(AiProviderConfig.OPENROUTER_CHAT_COMPLETIONS_URL, chatRequest.url.toString())
            assertEquals("Bearer $key", chatRequest.headers["Authorization"])
            assertFalse(chatRequest.url.toString().contains(key))
            assertTrue((chatRequest.body as TextContent).text.contains("\"model\":\"$model\""))

            assertEquals(AiResult.Success(Unit), tutor.testConnection())
            val keyRequest = requests.last()
            assertEquals(HttpMethod.Get, keyRequest.method)
            assertEquals(AiProviderConfig.OPENROUTER_KEY_URL, keyRequest.url.toString())
            assertEquals("Bearer $key", keyRequest.headers["Authorization"])
            assertFalse(keyRequest.url.toString().contains(key))

            settings.setInterviewMode(true)
            val interviewError = AiResult.Error("AI tutor is unavailable in interview mode")
            assertEquals(interviewError, tutor.hint(exercise, level = 1))
            assertEquals(interviewError, tutor.testConnection())
            assertEquals("Interview mode must block before making a request", 2, requests.size)
        } finally {
            driver.close()
        }
    }

    @Test
    fun missingKeyFailsWithTheAddKeyMessageAndSendsNothing() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val tutor = wiredTutor(
            settings = AppSettings(selectedAiProvider = AiProvider.OPENAI),
            secretStore = FixedSecretStore(null),
            requests = requests
        )

        val result = tutor.hint(exercise, level = 1)

        assertEquals(AiResult.Error(AI_API_KEY_MISSING_MESSAGE), result)
        assertEquals(0, requests.size)
    }

    @Test
    fun interviewModeFailsWithoutSendingAnything() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val tutor = wiredTutor(
            settings = AppSettings(selectedAiProvider = AiProvider.OPENROUTER, interviewMode = true),
            secretStore = FixedSecretStore("test-key-123"),
            requests = requests
        )

        val result = tutor.explainMistake(exercise, "wrong", "right")

        assertEquals(AiResult.Error("AI tutor is unavailable in interview mode"), result)
        assertEquals(0, requests.size)
    }

    @Test
    fun loadAiAccessReportsKeyPresenceAndInterviewMode() = runBlocking {
        val withKey = loadAiAccess(
            FakeSettingsRepository(AppSettings(interviewMode = false)),
            FixedSecretStore("test-key-123")
        )
        assertEquals(AiAccess(hasApiKey = true, interviewMode = false), withKey)
        assertTrue(withKey.isUsable)

        val noKey = loadAiAccess(FakeSettingsRepository(), FixedSecretStore(null))
        assertEquals(AiAccess(hasApiKey = false, interviewMode = false), noKey)
        assertFalse(noKey.isUsable)

        val blankKey = loadAiAccess(FakeSettingsRepository(), FixedSecretStore("   "))
        assertFalse(blankKey.hasApiKey)

        val interview = loadAiAccess(
            FakeSettingsRepository(AppSettings(interviewMode = true)),
            FixedSecretStore("test-key-123")
        )
        assertTrue(interview.hasApiKey)
        assertFalse("interview mode turns AI off even with a key", interview.isUsable)
    }

    @Test
    fun aSecretStoreThatFailsCountsAsNoKey() = runBlocking {
        val access = loadAiAccess(FakeSettingsRepository(), FixedSecretStore(failing = true))

        assertFalse(access.hasApiKey)
    }
}
