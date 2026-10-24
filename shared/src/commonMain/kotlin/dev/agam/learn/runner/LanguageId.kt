package dev.agam.learn.runner

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Strongly-typed identifier for programming languages supported by the platform (e.g. "kotlin").
 */
@JvmInline
@Serializable
value class LanguageId(val value: String) {
    override fun toString(): String = value

    companion object {
        val KOTLIN = LanguageId("kotlin")
    }
}
