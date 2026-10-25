package dev.agam.learn.ai

import dev.agam.learn.data.SecretStore
import dev.agam.learn.model.Exercise
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
                {"id":"msg_test","type":"message","role":"assistant","content":[{"type":"thinking","thinking":"private chain"},{"type":"text","text":"Use ?. "},{"type":"tool_use","id":"tool-test","name":"private_tool","input":{"secret":"private payload"}},{"type":"text","text":"before reading the value."}],"model":"claude-haiku-5-5","stop_reason":"end_turn"}
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
                {"id":"resp_test","object":"response","status":"completed","output":[{"type":"reasoning","summary":[{"type":"summary_text","text":"private chain"}]},{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Try a nullable-safe call."}]},{"type":"function_call","name":"private_tool","arguments":"{\"secret\":\"private payload\"}"}]}
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
        assertFalse(body.containsKey("temperature"))
        assertEquals(AiClientResult.Text("Try a nullable-safe call."), result)
    }

    @Test
    fun openAiDoesNotTreatIncompleteOrFailedResponsesAsSuccess() = runTest {
        val incomplete = mockClient {
            respondJson(
                """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[{"type":"message","content":[{"type":"output_text","text":"partial answer"}]}]}"""
            )
        }
        assertEquals(
            AiClientResult.Failure(reason = AiFailureReason.INCOMPLETE_RESPONSE),
            OpenAiClient(incomplete).chat("synthetic-key", "system", "user", 64)
        )
        incomplete.close()

        val failed = mockClient {
            respondJson(
                """{"status":"failed","error":{"message":"private provider details and credential"},"output":[{"type":"message","content":[{"type":"output_text","text":"partial answer"}]}]}"""
            )
        }
        assertEquals(
            AiClientResult.Failure(reason = AiFailureReason.UNKNOWN),
            OpenAiClient(failed).chat("synthetic-key", "system", "user", 64)
        )
        failed.close()
    }

    @Test
    fun anthropicMaxTokenStopIsIncompleteEvenWhenPartialTextExists() = runTest {
        val client = mockClient {
            respondJson(
                """{"content":[{"type":"text","text":"partial answer"},{"type":"thinking","thinking":"private chain"},{"type":"tool_use","input":{"secret":"tool payload"}}],"stop_reason":"max_tokens"}"""
            )
        }

        assertEquals(
            AiClientResult.Failure(reason = AiFailureReason.INCOMPLETE_RESPONSE),
            AnthropicAiClient(client).chat("synthetic-key", "system", "user", 64)
        )
        client.close()
    }

    @Test
    fun geminiRequestOmitsTemperatureAndParsesSuccess() = runTest {
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

        assertEquals(
            AiProviderConfig.geminiGenerateContentUrl(AiProviderConfig.GEMINI_DEFAULT_MODEL),
            request!!.url.toString()
        )
        assertEquals("test-gemini-key", request!!.headers["x-goog-api-key"])
        assertTrue(request!!.body.contentType?.match(ContentType.Application.Json) == true)
        assertEquals("32", generationConfig["maxOutputTokens"]?.toString())
        assertFalse(generationConfig.containsKey("temperature"))
        assertEquals(AiClientResult.Text("Use a when expression."), result)
    }

    @Test
    fun geminiParsesResponseMetadataAndSkipsThoughtParts() = runTest {
        val client = mockClient {
            respondJson(
                """
                {
                  "candidates":[{"content":{"parts":[
                    {"text":"private reasoning", "thought":true},
                    {"text":"Here is the answer.", "thought":false}
                  ],"role":"model"},"finishReason":"STOP"}],
                  "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":8,"totalTokenCount":18},
                  "modelVersion":"gemini-3.5-flash-lite-001",
                  "responseId":"response-test"
                }
                """.trimIndent()
            )
        }

        assertEquals(
            AiClientResult.Text("Here is the answer."),
            GeminiClient(client, "models/gemini-3.5-flash-lite").chat(
                "test-gemini-key", "system", "user", 32
            )
        )
        client.close()
    }

    @Test
    fun geminiReportsBlockedEmptyAndIncompleteResponsesSeparately() = runTest {
        val cases = listOf(
            """{"promptFeedback":{"blockReason":"SAFETY"}}""" to AiFailureReason.BLOCKED_RESPONSE,
            """{"candidates":[{"content":{"parts":[{"text":"thought","thought":true}]},"finishReason":"STOP"}]}""" to AiFailureReason.EMPTY_RESPONSE,
            """{"candidates":[{"content":{"parts":[{"text":"partial"}]},"finishReason":"MAX_TOKENS"}]}""" to AiFailureReason.INCOMPLETE_RESPONSE
        )
        for ((body, reason) in cases) {
            val client = mockClient { respondJson(body) }
            assertEquals(AiClientResult.Failure(reason = reason), GeminiClient(client).chat("test-key", "system", "user", 32))
            client.close()
        }
    }

    @Test
    fun openRouterUsesChatCompletionsShapeAndParsesSuccess() = runTest {
        var request: HttpRequestData? = null
        val client = mockClient { data ->
            request = data
            respondJson(
                """
                {"id":"gen_test","choices":[{"message":{"role":"assistant","content":"Use an Elvis operator."},"finish_reason":"stop"}]}
                """
            )
        }

        val result = OpenRouterAiClient(client, "provider/example-model").chat(
            "test-openrouter-secret",
            "Be a patient tutor.",
            "How do I handle a nullable value?",
            58
        )
        val body = requestBody(request!!).jsonObject
        val messages = body["messages"]!!.toString()

        assertEquals(HttpMethod.Post, request!!.method)
        assertEquals(AiProviderConfig.OPENROUTER_CHAT_COMPLETIONS_URL, request!!.url.toString())
        assertEquals("Bearer test-openrouter-secret", request!!.headers["Authorization"])
        assertFalse(request!!.url.toString().contains("test-openrouter-secret"))
        assertEquals("provider/example-model", body["model"]?.toString()?.trim('"'))
        assertEquals("58", body["max_completion_tokens"]?.toString())
        assertFalse(body.containsKey("temperature"))
        assertTrue(messages.contains("system"))
        assertTrue(messages.contains("Be a patient tutor."))
        assertTrue(messages.contains("user"))
        assertTrue(messages.contains("How do I handle a nullable value?"))
        assertEquals(AiClientResult.Text("Use an Elvis operator."), result)
        client.close()
    }

    @Test
    fun openRouterTutorUsesDefaultAndConfiguredModel() = runTest {
        assertEquals("openrouter/auto", AiProviderConfig.defaultModel(AiProvider.OPENROUTER))

        for ((configuredModel, expectedModel) in listOf(
            null to AiProviderConfig.OPENROUTER_DEFAULT_MODEL,
            "provider/custom-model" to "provider/custom-model"
        )) {
            var request: HttpRequestData? = null
            val client = mockClient { data ->
                request = data
                respondJson(
                    """{"id":"gen_test","model":"provider/served-model","choices":[{"index":0,"message":{"role":"assistant","content":"A hint."},"finish_reason":"stop"}],"usage":{"total_tokens":4}}"""
                )
            }
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = AiProvider.OPENROUTER, model = configuredModel),
                secretStore = StaticSecretStore("test-key"),
                httpClient = client
            )

            assertEquals(AiResult.Success("A hint."), tutor.hint(sampleExercise(), 1))
            assertEquals(expectedModel, requestBody(request!!).jsonObject["model"]?.toString()?.trim('"'))
            client.close()
        }
    }

    @Test
    fun openRouterRejectsErrorObjectsAndMissingOrEmptyChoicesOnSuccessStatus() = runTest {
        val invalidBodies = listOf(
            """{"error":{"message":"request could not be routed"}}""",
            """{"id":"gen_test","model":"provider/model"}""",
            """{"id":"gen_test","choices":[]}""",
            """{"id":"gen_test","choices":[{"message":{"role":"assistant","content":"  "}}]}"""
        )

        for (body in invalidBodies) {
            val client = mockClient { respondJson(body) }
            assertIs<AiClientResult.Failure>(
                OpenRouterAiClient(client).chat("test-key", "system", "user", 32),
                body
            )
            client.close()
        }
    }

    @Test
    fun eachProviderUsesAuthenticatedEndpointForConnectionTest() = runTest {
        val cases = listOf(
            Triple(AiProvider.ANTHROPIC, AiProviderConfig.ANTHROPIC_MODELS_URL, "anthropic-version"),
            Triple(AiProvider.OPENAI, AiProviderConfig.OPENAI_MODELS_URL, "Authorization"),
            Triple(AiProvider.GEMINI, AiProviderConfig.GEMINI_MODELS_URL, "x-goog-api-key"),
            Triple(AiProvider.OPENROUTER, AiProviderConfig.OPENROUTER_KEY_URL, "Authorization")
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
                AiProvider.OPENROUTER -> OpenRouterAiClient(client)
            }

            assertIs<AiClientResult.Text>(providerClient.testConnection("test-key"))
            assertEquals(HttpMethod.Get, request!!.method)
            assertEquals(expectedUrl, request!!.url.toString())
            assertTrue(request!!.headers[expectedHeader] != null)
            if (provider == AiProvider.OPENROUTER) {
                assertEquals("Bearer test-key", request!!.headers["Authorization"])
                assertFalse(request!!.url.toString().contains("test-key"))
            }
            client.close()
        }
    }

    @Test
    fun modelCatalogRequestsAuthenticateAndFilterCompatibleModels() = runTest {
        val cases = listOf(
            AiProvider.OPENAI to """{"data":[{"id":"gpt-6-luna"},{"id":"text-embedding-3-large"},{"id":"gpt-4o-mini-tts"},{"id":"o4-mini"}]}""",
            AiProvider.ANTHROPIC to """{"data":[{"id":"claude-haiku-5-5"},{"id":"claude-opus-5-5"}],"has_more":false,"last_id":"claude-opus-5-5"}""",
            AiProvider.GEMINI to """{"models":[{"name":"models/gemini-exact-version","baseModelId":"gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent"]},{"baseModelId":"gemini-alias-fallback","supportedGenerationMethods":["generateContent"]},{"baseModelId":"text-embedding-001","supportedGenerationMethods":["embedContent"]}]}""",
            AiProvider.OPENROUTER to """{"data":[{"id":"openai/gpt-4o-mini","architecture":{"output_modalities":["text"]}},{"id":"provider/image-model","architecture":{"output_modalities":["image"]}}]}"""
        )
        for ((provider, body) in cases) {
            var request: HttpRequestData? = null
            val client = mockClient { data ->
                request = data
                respondJson(body)
            }
            val result = AiTutorImpl(
                settings = AiSettings(provider = provider),
                secretStore = StaticSecretStore("synthetic-key"),
                httpClient = client
            ).listModels()
            val models = assertIs<AiResult.Success<List<String>>>(result).data
            assertTrue(models.isNotEmpty(), "$provider")
            assertTrue(request!!.headers["Authorization"] != null || request!!.headers["x-goog-api-key"] != null)
            assertFalse(request!!.url.toString().contains("synthetic-key"))
            if (provider == AiProvider.OPENAI) assertEquals(listOf("gpt-6-luna", "o4-mini"), models)
            if (provider == AiProvider.GEMINI) {
                assertEquals(listOf("gemini-exact-version", "gemini-alias-fallback"), models)
            }
            if (provider == AiProvider.OPENROUTER) {
                assertTrue(request!!.url.toString().contains("output_modalities=text"))
                assertEquals(listOf("openai/gpt-4o-mini"), models)
            }
            client.close()
        }
    }

    @Test
    fun modelCatalogsFollowAnthropicAndGeminiPagination() = runTest {
        val anthropicRequests = mutableListOf<HttpRequestData>()
        val anthropic = mockClient { data ->
            anthropicRequests += data
            if (anthropicRequests.size == 1) {
                respondJson("""{"data":[{"id":"claude-first"}],"has_more":true,"last_id":"claude-first"}""")
            } else {
                respondJson("""{"data":[{"id":"claude-second"}],"has_more":false,"last_id":"claude-second"}""")
            }
        }
        assertEquals(
            AiResult.Success(listOf("claude-first", "claude-second")),
            AiTutorImpl(AiSettings(AiProvider.ANTHROPIC), StaticSecretStore("synthetic-key"), anthropic).listModels()
        )
        assertEquals(2, anthropicRequests.size)
        assertTrue(anthropicRequests[1].url.toString().contains("after_id=claude-first"))
        anthropic.close()

        val geminiRequests = mutableListOf<HttpRequestData>()
        val gemini = mockClient { data ->
            geminiRequests += data
            if (geminiRequests.size == 1) {
                respondJson("""{"models":[{"name":"models/gemini-first-v2","baseModelId":"gemini-first","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"next/page"}""")
            } else {
                respondJson("""{"models":[{"name":"models/gemini-second-v3","baseModelId":"gemini-second","supportedGenerationMethods":["generateContent"]}]}""")
            }
        }
        assertEquals(
            AiResult.Success(listOf("gemini-first-v2", "gemini-second-v3")),
            AiTutorImpl(AiSettings(AiProvider.GEMINI), StaticSecretStore("synthetic-key"), gemini).listModels()
        )
        assertEquals(2, geminiRequests.size)
        assertTrue(geminiRequests[1].url.toString().contains("pageToken=next%2Fpage"))
        gemini.close()
    }

    @Test
    fun customModelIdsAreUsedByEveryProviderClient() = runTest {
        for (provider in AiProvider.entries) {
            var request: HttpRequestData? = null
            val client = mockClient { data ->
                request = data
                val body = when (provider) {
                    AiProvider.ANTHROPIC -> """{"content":[{"type":"text","text":"Answer"}]}"""
                    AiProvider.OPENAI -> """{"output":[{"type":"message","content":[{"type":"output_text","text":"Answer"}]}]}"""
                    AiProvider.GEMINI -> """{"candidates":[{"content":{"parts":[{"text":"Answer"}]},"finishReason":"STOP"}]}"""
                    AiProvider.OPENROUTER -> """{"choices":[{"message":{"content":"Answer"}}]}"""
                }
                respondJson(body)
            }
            val enteredModel = if (provider == AiProvider.GEMINI) "models/gemini-custom-id" else "vendor/custom-model-id"
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = provider, model = enteredModel),
                secretStore = StaticSecretStore("synthetic-key"),
                httpClient = client
            )

            assertEquals(AiResult.Success("Answer"), tutor.hint(sampleExercise(), 1), "$provider")
            if (provider == AiProvider.GEMINI) {
                assertTrue(request!!.url.toString().contains("/models/gemini-custom-id:generateContent"))
            } else {
                assertEquals(enteredModel, requestBody(request!!).jsonObject["model"]?.toString()?.trim('"'))
            }
            client.close()
        }
    }

    @Test
    fun tutorUsesReasoningSafeOutputFloorAndPreservesLargerCardBudget() = runTest {
        for (provider in AiProvider.entries) {
            var request: HttpRequestData? = null
            val client = mockClient { data ->
                request = data
                val body = when (provider) {
                    AiProvider.ANTHROPIC -> """{"content":[{"type":"text","text":"Hint"}],"stop_reason":"end_turn"}"""
                    AiProvider.OPENAI -> """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"Hint"}]}]}"""
                    AiProvider.GEMINI -> """{"candidates":[{"content":{"parts":[{"text":"Hint"}]},"finishReason":"STOP"}]}"""
                    AiProvider.OPENROUTER -> """{"choices":[{"message":{"content":"Hint"}}]}"""
                }
                respondJson(body)
            }
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = provider),
                secretStore = StaticSecretStore("synthetic-key"),
                httpClient = client
            )

            assertEquals(AiResult.Success("Hint"), tutor.hint(sampleExercise(), 1), "$provider")
            val body = requestBody(request!!).jsonObject
            val tokenLimit = when (provider) {
                AiProvider.ANTHROPIC -> body["max_tokens"]
                AiProvider.OPENAI -> body["max_output_tokens"]
                AiProvider.GEMINI -> body["generationConfig"]!!.jsonObject["maxOutputTokens"]
                AiProvider.OPENROUTER -> body["max_completion_tokens"]
            }
            assertEquals("2048", tokenLimit?.toString(), "$provider")
            client.close()
        }

        var request: HttpRequestData? = null
        val cards = (1..12).joinToString(",") { index ->
            """{"type":"multiple_choice","prompt":"Question $index","options":["a","b"],"correctIndex":0,"explanation":"Because."}"""
        }
        val cardJson = """{"cards":[$cards]}"""
        val client = mockClient { data ->
            request = data
            respondJson(
                """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":${json.encodeToString(String.serializer(), cardJson)}}]}]}"""
            )
        }
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENAI),
            secretStore = StaticSecretStore("synthetic-key"),
            httpClient = client
        )
        assertIs<AiResult.Success<List<Exercise>>>(tutor.generateCards("null safety", 12))
        assertEquals("2160", requestBody(request!!).jsonObject["max_output_tokens"]?.toString())
        client.close()
    }

    @Test
    fun tutorConnectionTestSucceedsWithoutGenerating() = runTest {
        for (provider in AiProvider.entries) {
            var request: HttpRequestData? = null
            val client = mockClient { data ->
                request = data
                respondJson("{}")
            }
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = provider),
                secretStore = StaticSecretStore("test-key"),
                httpClient = client
            )

            assertIs<AiResult.Success<Unit>>(tutor.testConnection())
            assertEquals(HttpMethod.Get, request!!.method, "$provider must use GET")
            client.close()
        }
    }

    @Test
    fun tutorConnectionTestMapsBadKey() = runTest {
        for (provider in AiProvider.entries) {
            val client = mockClient { respondJson("{}", status = 401) }
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = provider),
                secretStore = StaticSecretStore("bad-key"),
                httpClient = client
            )

            assertEquals(AiResult.Error("API key rejected"), tutor.testConnection(), "$provider")
            client.close()
        }
    }

    @Test
    fun tutorConnectionTestRefusesInterviewModeWithoutRequest() = runTest {
        var requestCount = 0
        val client = mockClient {
            requestCount++
            respondJson("{}")
        }
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENAI, interviewMode = true),
            secretStore = StaticSecretStore("test-key"),
            httpClient = client
        )

        assertEquals(
            AiResult.Error("AI tutor is unavailable in interview mode"),
            tutor.testConnection()
        )
        assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun tutorConnectionTestRequiresKeyWithoutRequest() = runTest {
        var requestCount = 0
        val client = mockClient {
            requestCount++
            respondJson("{}")
        }
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENAI),
            secretStore = StaticSecretStore(null),
            httpClient = client
        )

        assertEquals(AiResult.Error("Add your API key in Settings"), tutor.testConnection())
        assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun cancellingTutorCallPropagatesCancellationException() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val client = mockClient {
            requestStarted.complete(Unit)
            awaitCancellation()
        }
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENAI),
            secretStore = StaticSecretStore("test-key"),
            httpClient = client
        )
        val call = async { tutor.hint(sampleExercise(), 1) }

        requestStarted.await()
        call.cancel()
        assertFailsWith<CancellationException> { call.await() }
        client.close()
    }

    @Test
    fun tutorMapsProviderErrorsForEveryProvider() = runTest {
        val expected = mapOf(
            401 to "API key rejected",
            403 to "Provider denied access. Check API key permissions and account access.",
            429 to "Provider rate limit or usage quota reached. Check account limits or try again shortly.",
            500 to "Provider busy. Try again shortly.",
            503 to "Provider busy. Try again shortly.",
            529 to "Provider busy. Try again shortly."
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
    fun invalidModelAndBadRequestAreActionableAndSanitized() = runTest {
        val cases = listOf(
            400 to "Provider rejected the request. Check the selected model and its supported settings.",
            404 to "Model not found. Check the model ID or refresh the model list."
        )
        for ((status, message) in cases) {
            val client = mockClient {
                respondJson("""{"error":{"message":"secret provider details and test-key"}}""", status)
            }
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = AiProvider.GEMINI, model = "models/gemini-custom"),
                secretStore = StaticSecretStore("synthetic-key"),
                httpClient = client
            )
            val result = tutor.hint(sampleExercise(), 1)
            assertEquals(AiResult.Error(message), result)
            assertFalse(result.toString().contains("secret provider details"))
            assertFalse(result.toString().contains("test-key"))
            client.close()
        }
    }

    @Test
    fun geminiInvalidApiKeyErrorBodyIsSanitized() = runTest {
        val client = mockClient {
            respondJson(
                """{"error":{"code":400,"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}],"message":"raw key detail"}}""",
                status = 400
            )
        }
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.GEMINI),
            secretStore = StaticSecretStore("synthetic-key"),
            httpClient = client
        )
        assertEquals(AiResult.Error("API key rejected"), tutor.testConnection())
        client.close()
    }

    @Test
    fun tutorMapsOpenRouterInsufficientCredits() = runTest {
        val client = mockClient { respondJson("{}", status = 402) }
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENROUTER),
            secretStore = StaticSecretStore("test-key"),
            httpClient = client
        )

        assertEquals(
            AiResult.Error("OpenRouter credits or spending limit reached"),
            tutor.hint(sampleExercise(), 1)
        )
        client.close()
    }

    @Test
    fun networkFailureMapsToNoConnection() = runTest {
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler { throw UnknownHostException() }
        })
        val client = testHttpClient(engine)
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENAI),
            secretStore = StaticSecretStore("test-key"),
            httpClient = client
        )

        assertEquals(AiResult.Error("No connection. Check your internet and try again."), tutor.hint(sampleExercise(), 1))
        client.close()

        val nonTransport = mockClient { throw IllegalStateException("parser or provider bug") }
        val nonTransportTutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.OPENAI),
            secretStore = StaticSecretStore("test-key"),
            httpClient = nonTransport
        )
        assertEquals(AiResult.Error("AI provider request failed"), nonTransportTutor.hint(sampleExercise(), 1))
        nonTransport.close()
    }

    @Test
    fun malformedSuccessBodyIsAnInvalidResponseRatherThanOffline() = runTest {
        for (provider in AiProvider.entries) {
            val client = mockClient { respondJson("not valid provider JSON") }
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = provider),
                secretStore = StaticSecretStore("synthetic-key"),
                httpClient = client
            )
            assertEquals(
                AiResult.Error("Provider returned an unreadable response. Try again or choose another model."),
                tutor.hint(sampleExercise(), 1),
                "$provider"
            )
            client.close()
        }
    }

    @Test
    fun secureConnectionFailureIsDistinctFromOffline() = runTest {
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler { throw SSLHandshakeException() }
        })
        val client = testHttpClient(engine)
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.GEMINI),
            secretStore = StaticSecretStore("synthetic-key"),
            httpClient = client
        )

        assertEquals(
            AiResult.Error("Secure connection failed. Check your network or device date and time."),
            tutor.hint(sampleExercise(), 1)
        )
        client.close()
    }

    @Test
    fun requestTimeoutIsDistinctFromOffline() = runTest {
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler { throw ConnectTimeoutException() }
        })
        val client = testHttpClient(engine)
        val tutor = AiTutorImpl(
            settings = AiSettings(provider = AiProvider.GEMINI),
            secretStore = StaticSecretStore("synthetic-key"),
            httpClient = client
        )

        assertEquals(
            AiResult.Error("Provider timed out. Try again shortly."),
            tutor.hint(sampleExercise(), 1)
        )
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
        for (provider in AiProvider.entries) {
            var requestCount = 0
            val client = mockClient {
                requestCount++
                respondJson("{}")
            }
            val tutor = AiTutorImpl(
                settings = AiSettings(provider = provider, interviewMode = true),
                secretStore = StaticSecretStore("test-key"),
                httpClient = client
            )

            val result = tutor.explainMistake(sampleExercise(), "wrong", "right")
            assertEquals(AiResult.Error("AI tutor is unavailable in interview mode"), result, "$provider")
            assertEquals(0, requestCount, "$provider")
            client.close()
        }
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

    private fun testHttpClient(engine: MockEngine): HttpClient = createAiHttpClient(engine)

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

    private class ConnectTimeoutException : Exception()
    private class SSLHandshakeException : Exception()
    private class UnknownHostException : Exception()

    private class StaticSecretStore(private val key: String?) : SecretStore {
        override suspend fun get(): String? = key
        override suspend fun set(value: String) {}
        override suspend fun delete() {}
    }
}
