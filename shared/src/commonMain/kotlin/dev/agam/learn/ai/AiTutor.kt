package dev.agam.learn.ai

import dev.agam.learn.model.Exercise

/**
 * Contract for the optional AI tutor assistant.
 */
interface AiTutor {
    /** Tests provider connectivity without generating a response. */
    suspend fun testConnection(): AiResult<Unit> =
        AiResult.Error("Testing the AI connection is not supported")

    /** Lists text-generation models visible to the saved provider key, when supported. */
    suspend fun listModels(): AiResult<List<String>> =
        AiResult.Error("Model discovery is not supported by this AI tutor")

    /**
     * Explains why an answer was incorrect in plain language.
     */
    suspend fun explainMistake(
        exercise: Exercise,
        userAnswer: String,
        expectedResult: String
    ): AiResult<String>

    /**
     * Provides an incremental hint (level 1 = nudge, 2 = approach, 3 = near-solution).
     */
    suspend fun hint(
        exercise: Exercise,
        level: Int
    ): AiResult<String>

    /**
     * Generates supplementary practice cards on a topic.
     */
    suspend fun generateCards(
        topic: String,
        count: Int = 3
    ): AiResult<List<Exercise>>

    /**
     * Reviews passing learner code for readability and idiomatic conventions.
     */
    suspend fun reviewCode(
        exercise: Exercise,
        code: String
    ): AiResult<String>
}
