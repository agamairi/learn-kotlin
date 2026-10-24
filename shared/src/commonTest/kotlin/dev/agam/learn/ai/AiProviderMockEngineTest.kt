package dev.agam.learn.ai

import dev.agam.learn.data.SecretStore
import dev.agam.learn.model.Exercise
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiProviderMockEngineTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun anthropicRequestShapeAndSuccessParsing() = runTest {
        var request: HttpRequestData? = null
        val client = mockClient { data ->
            request = data
            respondJson(
                """
                {"id":"msg_test","type":"message","role":"assistant","content":[{"type":"text","text":"Use ?. before reading the value."}],"model":"claude-haiku-5-5","stop_reason":"end_turn"}
                """
            )
        }

        val result = AnthropicAiClient(client).chat("test-anthropic-key", "system", "user", 42)
        val body = requestBody(request!!).jsonObject

        assertEquals(HttpMethod.Post, request!!.method)
        assertEquals(AiProviderConfig.ANTHROPIC_MESSAGES_URL, request!!.url.toString())
        assertEquals("Bearer test-anthropic-key", request!!.headers["Authorization"])
        assertEquals(AiProviderConfig.ANTHROPIC_VERSION, request!!.headers["anthropic-version"])
        assertEquals("claude-haiku-5-5", body["model"]?.toString()?.trim('"'))
        assertEquals("42", body["max_tokens"]?.toString())
        assertTrue(body["system"] != null)
        assertTrue(body["messages"] != null)
        assertFalse(body.containsKey("temperature"))
        assertEquals(AiClientResult.Text("Use ?. before reading the value."), result)
    }

    @Test
    fun openAiRequestShapeAndSuccessParsing() = runTest {
        var request: HttpRequestData? = null
        val client = mockClient { data ->
            request = data
            respondJson(
                """
                {"id":"resp_test","object":"response","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Try a nullable-safe call."}]}]}
                """
            )
        }

        val result = OpenAiClient(client).chat("test-openai-key", "system", "user", 64)
        val body = requestBody(request!!).jsonObject

        assertEquals(AiProviderConfig.OPENAI_RESPONSES_URL, request!!.url.toString())
        assertEquals("Bearer test-openai-key", request!!.headers["Authorization"])
        assertEquals("gpt-6-luna", body["model"]?.toString()?.trim('"'))
        assertEquals("system", body["instructions"]?.toString()?.trim('"'))
        assertEquals("user", body["input"]?.toString()?.trim('"'))
        assertEquals("64", body["max_output_tokens"]?.toString())
        assertEquals(AiClientResult.Text("Try a nullable-safe call."), result)
    }

    @Test
    fun gemini4RequestOmitsTemperatureAndParsesSuccess() = runTest {
        var request: HttpRequestData? = null
        val client = mockClient { data ->
            request = data
            respondJson(
                """
                {"candidates":[{"content":{"parts":[{"text":"Use a when expression."}],"role":"model"},"finishReason":"STOP"}]}
                """
            )
        }

        val result = GeminiClient(client).chat("test-gemini-key", "system", "user", 32)
        val body = requestBody(request!!).jsonObject
        val generationConfig = body["generationConfig"]!!.jsonObject

        assertEquals(AiProviderConfig.GEMINI_BASE_URL + "/models/gemini-4-argon:generateContent", request!!.url.toString())
        assertEquals("test-gemini-key", request!!.headers["x-goog-api-key"])
        assertTrue(request!!.body.contentType?.match(ContentType.Application.Json) == true)
        assertEquals("32", generationConfig["maxOutputTokens"]?.toString())
        assertFalse(generationConfig.containsKey("temperature"))
        assertEquals(AiClientResult.Text("Use a when expression."), result)
    }

    @Test
    fun eachProviderUsesModelsEndpointForConnectionTest() = runTest {
        val cases = listOf(
            Triple(AiProvider.ANTHROPIC, AiProviderConfig.ANTHROPIC_MODELS_URL, "anthropic-version"),
            Triple(AiProvider.OPENAI, AiProviderConfig.OPENAI_MODELS_URL, "Authorization"),
            Triple(AiProvider.GEMINI, AiProviderConfig.GEMINI_MODELS_URL, "x-goog-api-key")
        )

        for ((provider, expectedUrl, expectedHeader) in cases) {
            var request: HttpRequestData? = null
            val client = mockClient { data ->
                request = data
                respondJson("{}")
            }
            val providerClient = when (provider) {
                AiProvider.ANTHROPIC -> AnthropicAiClient(client)
                AiProvider.OPENAI -> OpenAiClient(client)
                AiProvider.GEMINI -> GeminiClient(client)
            }

            assertIs<AiClientResult.Text>(providerClient.testConnection("test-key"))
            assertEquals(HttpMethod.Get, request!!.method)
            assertEquals(expectedUrl, request!!.url.toString())
            assertTrue(request!!.headers[expectedHeader] != null)
            client.close()
        }
    }

    @Test
    fun tutorMapsProviderErrorsForEveryProvider() = runTest {
        val expected = mapOf(
            401 to "API key rejected",
            403 to "API key rejected",
            429 to "Rate limit, try again shortly",
            500 to "Provider busy",
            503 to "Provider busy",
            529 to "Provider busy"
        )

        for (provider in AiProvider.entries) {
            for ((status, message) in expected) {
                val client = mockClient { respondJson("{}", status) }
                val tutor = AiTutorImpl(
                    settings = AiSettings(provider = provider),
                    secretStore = StaticSecretStore("test-key"),
                    httpClient = client
                )

                val result = tutor.hint(sampleExercise(), 1)
                assertEquals(AiResult.Error(message), result, "$provider status $status")
                client.close()
            }
        }
    }

    @Test
    fun networkFailureMapsToNoConnection() = runTest {
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler { error("offline") }
        })
        val client = testHttpClient(engine)
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENAI),
            secretStore = StaticSecretStore("test-key"),
            httpClient = client
        )

        assertEquals(AiResult.Error("No connection"), tutor.hint(sampleExercise(), 1))
        client.close()
    }

    @Test
    fun missingSavedKeyFailsBeforeAnyRequest() = runTest {
        for (provider in AiProvider.entries) {
            var requestCount = 0
            val client = mockClient {
                requestCount++
                respondJson("{}")
            }
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = provider),
                secretStore = StaticSecretStore(null),
                httpClient = client
            )

            assertEquals(
                AiResult.Error("Add your API key in Settings"),
                tutor.hint(sampleExercise(), 1),
                "$provider"
            )
            assertEquals(0, requestCount, "$provider must not call the network without a key")
            client.close()
        }
    }

    @Test
    fun interviewModeBlocksWithoutMakingARequest() = runTest {
        var requestCount = 0
        val client = mockClient {
            requestCount++
            respondJson("{}")
        }
        val tutor = AiTutorImpl(
            settings = AiSettings(
                provider = AiProvider.OPENAI,
                interviewMode = true
            ),
            secretStore = StaticSecretStore("test-key"),
            httpClient = client
        )

        val result = tutor.explainMistake(sampleExercise(), "wrong", "right")
        assertEquals(AiResult.Error("AI tutor is unavailable in interview mode"), result)
        assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun generatedCardsAreStrictlyParsedAndMarkedAiGenerated() = runTest {
        val cardJson = """
            {"cards":[
              {"type":"multiple_choice","prompt":"Which is immutable?","code":null,"options":["val","var"],"correctIndex":0,"explanation":"val cannot be reassigned."},
              {"type":"cloze","prompt":"Call safely.","template":"user.____1()","blanks":[{"placeholder":"____1","accepted":["let"],"caseSensitive":true}],"explanation":"let runs only for non-null values."}
            ]
            }
        """.trimIndent()
        val response = """
            {"id":"resp_test","object":"response","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":${json.encodeToString(String.serializer(), cardJson)}}]}]}
        """.trimIndent()
        val client = mockClient { respondJson(response) }
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENAI),
            secretStore = StaticSecretStore("test-key"),
            httpClient = client
        )

        val result = tutor.generateCards("null safety", 2)
        val cards = assertIs<AiResult.Success<List<Exercise>>>(result).data
        assertEquals(2, cards.size)
        assertTrue((cards[0] as Exercise.MultipleChoice).aiGenerated)
        assertTrue((cards[1] as Exercise.Cloze).aiGenerated)
        client.close()
    }

    private fun sampleExercise(): Exercise = Exercise.MultipleChoice(
        id = "sample",
        prompt = "What is nullable?",
        options = listOf("String?", "String"),
        correctIndex = 0,
        explanation = "The question mark makes a type nullable."
    )

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient =
        testHttpClient(MockEngine(MockEngineConfig().apply { addHandler(handler) }))

    private fun testHttpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json() }
    }

    private fun requestBody(request: HttpRequestData): kotlinx.serialization.json.JsonObject =
        json.parseToJsonElement((request.body as TextContent).text).jsonObject

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: Int = 200
    ): HttpResponseData =
        respond(
            content = ByteReadChannel(body),
            status = io.ktor.http.HttpStatusCode.fromValue(status),
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )

    private class StaticSecretStore(private val key: String?) : SecretStore {
        override suspend fun get(): String? = key
        override suspend fun set(value: String) {}
        override suspend fun delete() {}
    }
}
