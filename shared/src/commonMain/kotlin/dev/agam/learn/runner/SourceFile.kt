package dev.agam.learn.runner

import kotlinx.serialization.Serializable

/**
 * A source file with a file name and code text to run or compile.
 */
@Serializable
data class SourceFile(
    val name: String,
    val text: String
)
