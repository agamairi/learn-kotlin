package dev.agam.learn.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Renders simple Markdown formatting including headings, paragraphs, bullet points,
 * inline code, and fenced code blocks.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = parseMarkdownBlocks(markdown)

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = block.text,
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        softWrap = true
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = formatInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        softWrap = true
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            softWrap = true
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats inline bold (**text**) and inline code (`code`) into an [AnnotatedString].
 */
fun formatInlineMarkdown(text: String, accentColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("""(\*\*(.*?)\*\*|`([^`]+)`)""")
        val matches = regex.findAll(text)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val fullMatch = match.value
            if (fullMatch.startsWith("**") && fullMatch.endsWith("**")) {
                val inner = match.groupValues[2]
                val start = length
                append(inner)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
            } else if (fullMatch.startsWith("`") && fullMatch.endsWith("`")) {
                val inner = match.groupValues[3]
                val start = length
                append(inner)
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    ),
                    start,
                    length
                )
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

private sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()
    var codeBlockLang: String? = null

    for (line in lines) {
        val trimmed = line.trimEnd()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeBlockLang, codeBlockLines.joinToString("\n")))
                codeBlockLines.clear()
                codeBlockLang = null
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeBlockLang = trimmed.removePrefix("```").trim().ifEmpty { null }
            }
            continue
        }

        if (inCodeBlock) {
            codeBlockLines.add(line)
            continue
        }

        if (trimmed.isBlank()) continue

        when {
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.substring(2).trim()))
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(trimmed))
            }
        }
    }

    if (inCodeBlock && codeBlockLines.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeBlockLang, codeBlockLines.joinToString("\n")))
    }

    return blocks
}
