package dev.agam.learn.model

import kotlinx.serialization.Serializable

/**
 * A chapter containing a group of related lessons.
 */
@Serializable
data class Chapter(
    val id: String,
    val title: String,
    val summary: String,
    val lessons: List<Lesson>
)
