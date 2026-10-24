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
import io.ktor.serialization.kotlinx.json.json

/**
 * Creates the HTTP client used by the AI provider clients. Requests and responses are JSON.
 * Pass [engine] to route traffic through a specific engine, such as a mock engine in tests.
 */
fun createAiHttpClient(engine: HttpClientEngine? = null): HttpClient =
    if (engine == null) {
        HttpClient { install(ContentNegotiation) { json() } }
    } else {
        HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

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

    /** Tests the configured provider's inexpensive models endpoint. */
    suspend fun testConnection(): AiResult<Unit> {
        val blocked = blockedResult()
        if (blocked != null) return blocked
        return withClient { client, apiKey ->
            val result = client.testConnection(apiKey)
            when (result) {
                is AiClientResult.Text -> AiResult.Success(Unit)
                is AiClientResult.Failure -> failureResult(result)
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
            when (val result = client.chat(apiKey, Prompts.SYSTEM, userPrompt, maxTokens)) {
                is AiClientResult.Text -> AiResult.Success(result.value)
                is AiClientResult.Failure -> failureResult(result)
            }
        }
    }

    private suspend fun <T> withClient(
        block: suspend (AiChatClient, String) -> AiResult<T>
    ): AiResult<T> {
        val apiKey = try {
            secretStore.get()
        } catch (_: Throwable) {
            null
        }?.takeIf { it.isNotBlank() } ?: return AiResult.Error("Add your API key in Settings")
        return try {
            block(createClient(), apiKey)
        } catch (_: Throwable) {
            AiResult.Error("No connection")
        }
    }

    private fun createClient(): AiChatClient {
        val model = settings.model ?: AiProviderConfig.defaultModel(settings.provider)
        return when (settings.provider) {
            AiProvider.ANTHROPIC -> AnthropicAiClient(httpClient, model)
            AiProvider.OPENAI -> OpenAiClient(httpClient, model)
            AiProvider.GEMINI -> GeminiClient(httpClient, model)
        }
    }

    private fun blockedResult(): AiResult.Error? = when {
        settings.interviewMode -> AiResult.Error("AI tutor is unavailable in interview mode")
        !settings.enabled -> AiResult.Error("AI tutor is disabled")
        else -> null
    }

    private fun failureResult(result: AiClientResult): AiResult.Error = when (result) {
        is AiClientResult.Text -> AiResult.Error("AI provider returned an unexpected result")
        is AiClientResult.Failure -> when {
            result.networkFailure -> AiResult.Error("No connection")
            result.statusCode == 401 || result.statusCode == 403 -> AiResult.Error("API key rejected")
            result.statusCode == 429 -> AiResult.Error("Rate limit, try again shortly")
            result.statusCode == 529 || result.statusCode in 500..599 -> AiResult.Error("Provider busy")
            else -> AiResult.Error("AI provider request failed")
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
