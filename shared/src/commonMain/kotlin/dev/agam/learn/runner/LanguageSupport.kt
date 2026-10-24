package dev.agam.learn.runner

/**
 * Language-specific configuration and runner binding.
 */
interface LanguageSupport {
    val id: LanguageId
    val displayName: String
    val runner: CodeRunner
    val keywords: Set<String>
}
