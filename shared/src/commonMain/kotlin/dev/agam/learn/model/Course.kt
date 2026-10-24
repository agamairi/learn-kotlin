package dev.agam.learn.model

import dev.agam.learn.runner.LanguageId
import kotlinx.serialization.Serializable

/**
 * Top-level course model representing a complete curriculum for a language.
 * When compiled for the client application, full chapter objects are embedded.
 */
@Serializable
data class Course(
    val id: String,
    val language: LanguageId,
    val title: String,
    val description: String,
    val chapters: List<Chapter>
)
