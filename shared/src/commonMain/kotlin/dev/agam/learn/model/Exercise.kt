package dev.agam.learn.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Sealed hierarchy for all exercise types in learn-kotlin.
 * Exercises are polymorphic in JSON, discriminated by the "type" property.
 */
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed interface Exercise {
    val id: String
    val prompt: String

    @Serializable
    @SerialName("multiple_choice")
    data class MultipleChoice(
        override val id: String,
        override val prompt: String,
        val code: String? = null,
        val options: List<String>,
        @SerialName("correct_index")
        val correctIndex: Int,
        val explanation: String,
        @SerialName("ai_generated")
        val aiGenerated: Boolean = false
    ) : Exercise

    @Serializable
    @SerialName("cloze")
    data class Cloze(
        override val id: String,
        override val prompt: String,
        val template: String,
        val blanks: List<Blank>,
        val explanation: String,
        @SerialName("ai_generated")
        val aiGenerated: Boolean = false
    ) : Exercise

    @Serializable
    @SerialName("predict_output")
    data class PredictOutput(
        override val id: String,
        override val prompt: String,
        val code: String,
        @SerialName("expected_output")
        val expectedOutput: String,
        val explanation: String
    ) : Exercise

    @Serializable
    @SerialName("write_function")
    data class WriteFunction(
        override val id: String,
        override val prompt: String,
        @SerialName("starter_code")
        val starterCode: String,
        @SerialName("hidden_tests")
        val hiddenTests: String,
        @SerialName("reference_solution")
        val referenceSolution: String,
        val hints: List<String> = emptyList(),
        val explanation: String? = null
    ) : Exercise

    @Serializable
    @SerialName("multi_part_problem")
    data class MultiPartProblem(
        override val id: String,
        override val prompt: String,
        val story: String,
        val parts: List<ProblemPart>,
        val faq: List<FaqItem> = emptyList(),
        val discussion: String? = null,
        @SerialName("source_note")
        val sourceNote: String? = null
    ) : Exercise
}

@Serializable
data class Blank(
    val placeholder: String,
    val accepted: List<String>,
    @SerialName("case_sensitive")
    val caseSensitive: Boolean = true
)

@Serializable
data class ProblemPart(
    val id: String,
    val prompt: String,
    @SerialName("starter_code")
    val starterCode: String? = null,
    @SerialName("hidden_tests")
    val hiddenTests: String,
    @SerialName("reference_solution")
    val referenceSolution: String,
    val expected: String,
    val hints: List<String> = emptyList()
)

@Serializable
data class FaqItem(
    val question: String,
    val answer: String
)
