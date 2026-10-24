package dev.agam.learn.runner

import kotlinx.serialization.Serializable

/**
 * Diagnostic information (e.g. error, warning) produced by a compiler or language server.
 */
@Serializable
data class Diagnostic(
    val line: Int,
    val column: Int,
    val severity: String,
    val message: String
)
