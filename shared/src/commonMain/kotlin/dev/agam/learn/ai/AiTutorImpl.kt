package dev.agam.learn.ai

import dev.agam.learn.data.SecretStore
import dev.agam.learn.model.Blank
import dev.agam.learn.model.Exercise
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException

/**
 * Creates the HTTP client used by the AI provider clients. Requests and responses are JSON.
 * Pass [engine] to route traffic through a specific engine, such as a mock engine in tests.
 */
fun createAiHttpClient(engine: HttpClientEngine? = null): HttpClient =
    if (engine == null) {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = AI_CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = AI_REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = AI_SOCKET_TIMEOUT_MILLIS
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    } else {
        HttpClient(engine) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = AI_CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = AI_REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = AI_SOCKET_TIMEOUT_MILLIS
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

private const val AI_CONNECT_TIMEOUT_MILLIS = 15_000L
private const val AI_REQUEST_TIMEOUT_MILLIS = 60_000L
private const val AI_SOCKET_TIMEOUT_MILLIS = 60_000L

/** Default provider-agnostic implementation of [AiTutor]. */
class AiTutorImpl(
    private val settings: AiSettings,
    private val secretStore: SecretStore,
    private val httpClient: HttpClient = createAiHttpClient()
) : AiTutor {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    /** Checks credentials without generating a completion or incurring inference charges. */
    override suspend fun testConnection(): AiResult<Unit> {
        val blocked = blockedResult()
        if (blocked != null) return blocked
        return withClient { client, apiKey ->
            val result = client.testConnection(apiKey)
            when (result) {
                is AiClientResult.Text -> AiResult.Success(Unit)
                is AiClientResult.Models -> AiResult.Error("AI provider returned an unexpected result")
                is AiClientResult.Failure -> failureResult(result)
            }
        }
    }

    /** Lists models available to this provider key without generating a response. */
    override suspend fun listModels(): AiResult<List<String>> {
        val blocked = blockedResult()
        if (blocked != null) return blocked
        return withClient { client, apiKey ->
            when (val result = client.listModels(apiKey)) {
                is AiClientResult.Models -> AiResult.Success(result.values)
                is AiClientResult.Failure -> failureResult(result)
                is AiClientResult.Text -> AiResult.Error("AI provider returned an unexpected result")
            }
        }
    }

    override suspend fun explainMistake(
        exercise: Exercise,
        userAnswer: String,
        expectedResult: String
    ): AiResult<String> = complete(
        userPrompt = Prompts.explainMistake(exercise, userAnswer, expectedResult),
        maxTokens = 180
    )

    override suspend fun hint(exercise: Exercise, level: Int): AiResult<String> {
        if (level !in 1..3) return AiResult.Error("Hint level must be between 1 and 3")
        return complete(userPrompt = Prompts.hint(exercise, level), maxTokens = 140)
    }

    override suspend fun generateCards(topic: String, count: Int): AiResult<List<Exercise>> {
        if (count !in 1..20) return AiResult.Error("Card count must be between 1 and 20")
        return complete(
            userPrompt = Prompts.generateCards(topic, count),
            maxTokens = count * 180
        ).let { result ->
            when (result) {
                is AiResult.Error -> result
                is AiResult.Success -> parseCards(result.data, count)
            }
        }
    }

    override suspend fun reviewCode(exercise: Exercise, code: String): AiResult<String> = complete(
        userPrompt = Prompts.reviewCode(exercise, code),
        maxTokens = 220
    )

    private suspend fun complete(userPrompt: String, maxTokens: Int): AiResult<String> {
        val blocked = blockedResult()
        if (blocked != null) return blocked
        return withClient { client, apiKey ->
            // Thinking tokens count against several providers' output caps. A small tutor
            // answer can otherwise spend its entire 140-token budget on internal reasoning.
            when (val result = client.chat(apiKey, Prompts.SYSTEM, userPrompt, maxOf(MIN_OUTPUT_TOKENS, maxTokens))) {
                is AiClientResult.Text -> AiResult.Success(result.value)
                is AiClientResult.Models -> AiResult.Error("AI provider returned an unexpected result")
                is AiClientResult.Failure -> failureResult(result)
            }
        }
    }

    private suspend fun <T> withClient(
        block: suspend (AiChatClient, String) -> AiResult<T>
    ): AiResult<T> {
        val storedApiKey = try {
            secretStore.get()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return AiResult.Error("Unable to read the API key from secure storage")
        }
        val apiKey = storedApiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: return AiResult.Error("Add your API key in Settings")
        return try {
            block(createClient(), apiKey)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AiResult.Error("AI provider request failed")
        }
    }

    private fun createClient(): AiChatClient {
        val model = AiProviderConfig.normalizeModel(settings.provider, settings.model)
        return when (settings.provider) {
            AiProvider.ANTHROPIC -> AnthropicAiClient(httpClient, model)
            AiProvider.OPENAI -> OpenAiClient(httpClient, model)
            AiProvider.GEMINI -> GeminiClient(httpClient, model)
            AiProvider.OPENROUTER -> OpenRouterAiClient(httpClient, model)
        }
    }

    private fun blockedResult(): AiResult.Error? = when {
        settings.interviewMode -> AiResult.Error("AI tutor is unavailable in interview mode")
        !settings.enabled -> AiResult.Error("AI tutor is disabled")
        else -> null
    }

    private fun failureResult(result: AiClientResult): AiResult.Error = when (result) {
        is AiClientResult.Text,
        is AiClientResult.Models -> AiResult.Error("AI provider returned an unexpected result")
        is AiClientResult.Failure -> failureResult(result)
    }

    private fun failureResult(failure: AiClientResult.Failure): AiResult.Error = when {
        failure.networkFailure -> AiResult.Error("No connection. Check your internet and try again.")
        else -> when (failure.reason) {
            AiFailureReason.INVALID_CREDENTIALS -> AiResult.Error("API key rejected")
            AiFailureReason.PERMISSION_DENIED ->
                AiResult.Error("Provider denied access. Check API key permissions and account access.")
            AiFailureReason.INVALID_MODEL ->
                AiResult.Error("Model not found. Check the model ID or refresh the model list.")
            AiFailureReason.INVALID_REQUEST ->
                AiResult.Error("Provider rejected the request. Check the selected model and its supported settings.")
            AiFailureReason.RATE_LIMIT_OR_QUOTA ->
                AiResult.Error("Provider rate limit or usage quota reached. Check account limits or try again shortly.")
            AiFailureReason.CREDITS_REQUIRED -> AiResult.Error(
                if (settings.provider == AiProvider.OPENROUTER) {
                    "OpenRouter credits or spending limit reached"
                } else {
                    "Provider account needs credits or billing"
                }
            )
            AiFailureReason.PROVIDER_UNAVAILABLE -> AiResult.Error("Provider busy. Try again shortly.")
            AiFailureReason.REQUEST_TIMEOUT -> AiResult.Error("Provider timed out. Try again shortly.")
            AiFailureReason.SECURE_CONNECTION_FAILURE ->
                AiResult.Error("Secure connection failed. Check your network or device date and time.")
            AiFailureReason.EMPTY_RESPONSE ->
                AiResult.Error("Provider returned no text. Choose a text generation model and try again.")
            AiFailureReason.BLOCKED_RESPONSE ->
                AiResult.Error("Gemini blocked the response. Try a different prompt.")
            AiFailureReason.INCOMPLETE_RESPONSE ->
                AiResult.Error("Provider stopped before finishing. Try a shorter request or increase the output limit.")
            AiFailureReason.INVALID_RESPONSE ->
                AiResult.Error("Provider returned an unreadable response. Try again or choose another model.")
            AiFailureReason.UNKNOWN -> AiResult.Error("AI provider request failed")
        }
    }

    private fun parseCards(raw: String, expectedCount: Int): AiResult<List<Exercise>> = runCatching {
        val response = json.decodeFromString<GeneratedCardsResponse>(raw)
        require(response.cards.size == expectedCount) { "unexpected card count" }
        response.cards.mapIndexed { index, card -> card.toExercise(index) }
    }.fold(
        onSuccess = { AiResult.Success(it) },
        onFailure = { AiResult.Error("The provider returned invalid card JSON") }
    )

    private fun GeneratedCard.toExercise(index: Int): Exercise = when (type) {
        "multiple_choice" -> Exercise.MultipleChoice(
            id = "ai-multiple-choice-${index + 1}",
            prompt = prompt,
            code = code,
            options = options?.also { require(it.size in 2..5) }
                ?: error("multiple-choice card has no options"),
            correctIndex = correctIndex?.also { selected ->
                require(selected in options!!.indices)
            } ?: error("multiple-choice card has no correctIndex"),
            explanation = explanation,
            aiGenerated = true
        )
        "cloze" -> Exercise.Cloze(
            id = "ai-cloze-${index + 1}",
            prompt = prompt,
            template = template ?: error("cloze card has no template"),
            blanks = blanks?.also { require(it.isNotEmpty()) }?.map { it.toModel() }
                ?: error("cloze card has no blanks"),
            explanation = explanation,
            aiGenerated = true
        )
        else -> error("unsupported card type")
    }

    private fun GeneratedBlank.toModel(): Blank = Blank(
        placeholder = placeholder,
        accepted = accepted,
        caseSensitive = caseSensitive
    )

    private companion object {
        const val MIN_OUTPUT_TOKENS = 2_048
    }
}

@Serializable
private data class GeneratedCardsResponse(val cards: List<GeneratedCard>)

@Serializable
private data class GeneratedCard(
    val type: String,
    val prompt: String,
    val code: String? = null,
    val options: List<String>? = null,
    val correctIndex: Int? = null,
    val template: String? = null,
    val blanks: List<GeneratedBlank>? = null,
    val explanation: String
)

@Serializable
private data class GeneratedBlank(
    val placeholder: String,
    val accepted: List<String>,
    @SerialName("caseSensitive") val caseSensitive: Boolean = true
)
