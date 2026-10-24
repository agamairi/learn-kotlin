package dev.agam.learn.model

import kotlinx.serialization.Serializable

/**
 * A single bite-sized lesson composed of an introductory explanation and exercises.
 */
@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val explanation: String,
    val exercises: List<Exercise>
)
