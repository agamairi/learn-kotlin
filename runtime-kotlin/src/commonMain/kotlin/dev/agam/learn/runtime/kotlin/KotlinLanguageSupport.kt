package dev.agam.learn.runtime.kotlin

import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.LanguageId
import dev.agam.learn.runner.LanguageSupport

/**
 * Kotlin language support configuration, providing keyword syntax highlighting data
 * and binding to the Kotlin compiler runner.
 */
class KotlinLanguageSupport(
    override val runner: CodeRunner = KotlinCompilerServerRunner(baseUrl = "http://10.0.2.2:8080")
) : LanguageSupport {

    override val id: LanguageId = LanguageId.KOTLIN

    override val displayName: String = "Kotlin"

    override val keywords: Set<String> = setOf(
        // Hard keywords
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
        "if", "in", "interface", "is", "null", "object", "package", "return",
        "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
        "var", "when", "while",
        // Soft keywords & modifiers
        "abstract", "actual", "annotation", "by", "catch", "companion", "const",
        "constructor", "crossinline", "data", "delegate", "dynamic", "enum",
        "expect", "external", "field", "final", "finally", "get", "import",
        "infix", "init", "inline", "inner", "internal", "lateinit", "noinline",
        "open", "operator", "out", "override", "param", "private", "protected",
        "public", "receiver", "reified", "sealed", "set", "setparam", "suspend",
        "tailrec", "value", "vararg", "where"
    )
}
