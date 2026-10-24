package dev.agam.learn.ui.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation providing simple Kotlin syntax highlighting for keywords,
 * strings, numbers, and comments.
 */
class KeywordVisualTransformation(
    private val keywords: Set<String> = DEFAULT_KEYWORDS,
    private val keywordColor: Color = Color(0xFF6200EE),
    private val stringColor: Color = Color(0xFF2E7D32),
    private val commentColor: Color = Color(0xFF757575),
    private val numberColor: Color = Color(0xFFE65100)
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotated = buildAnnotatedString {
            append(raw)

            // 1. Comments: //.*
            val commentRegex = Regex("""//.*""")
            for (match in commentRegex.findAll(raw)) {
                addStyle(SpanStyle(color = commentColor), match.range.first, match.range.last + 1)
            }

            // 2. String literals: "([^"\\]|\\.)*"
            val stringRegex = Regex(""""([^"\\]|\\.)*"""")
            for (match in stringRegex.findAll(raw)) {
                addStyle(SpanStyle(color = stringColor), match.range.first, match.range.last + 1)
            }

            // 3. Numbers: \b\d+(\.\d+)?\b
            val numberRegex = Regex("""\b\d+(\.\d+)?\b""")
            for (match in numberRegex.findAll(raw)) {
                addStyle(SpanStyle(color = numberColor), match.range.first, match.range.last + 1)
            }

            // 4. Keywords: word boundaries
            val wordRegex = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\b""")
            for (match in wordRegex.findAll(raw)) {
                if (keywords.contains(match.value)) {
                    addStyle(
                        SpanStyle(
                            color = keywordColor,
                            fontWeight = FontWeight.Bold
                        ),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }

    companion object {
        val DEFAULT_KEYWORDS: Set<String> = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return",
            "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
            "var", "when", "while", "abstract", "actual", "annotation", "by", "catch",
            "companion", "const", "constructor", "crossinline", "data", "delegate",
            "dynamic", "enum", "expect", "external", "field", "final", "finally",
            "get", "import", "infix", "init", "inline", "inner", "internal",
            "lateinit", "noinline", "open", "operator", "out", "override", "param",
            "private", "protected", "public", "receiver", "reified", "sealed",
            "set", "setparam", "suspend", "tailrec", "value", "vararg", "where"
        )
    }
}
