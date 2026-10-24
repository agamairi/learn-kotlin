package dev.agam.learn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.runner.LanguageSupport
import dev.agam.learn.runtime.kotlin.KotlinLanguageSupport
import dev.agam.learn.ui.theme.extendedColors

/**
 * Monospace code block composable with syntax keyword, string, number, and comment highlighting.
 *
 * @param code Raw code snippet to display.
 * @param modifier Optional modifier for outer layout.
 * @param languageSupport Language configuration containing keywords; defaults to [KotlinLanguageSupport].
 */
@Composable
fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    languageSupport: LanguageSupport = remember { KotlinLanguageSupport() }
) {
    val extendedColors = MaterialTheme.extendedColors
    val scrollState = rememberScrollState()

    val annotatedText = remember(code, languageSupport.keywords, extendedColors) {
        highlightSyntax(
            code = code,
            keywords = languageSupport.keywords,
            keywordColor = extendedColors.codeKeyword,
            stringColor = extendedColors.codeString,
            numberColor = extendedColors.codeNumber,
            commentColor = extendedColors.codeComment
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.codeBackground)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .horizontalScroll(scrollState)
            .padding(14.dp)
    ) {
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Highlights keywords, strings, comments, and numbers in the given source code.
 */
fun highlightSyntax(
    code: String,
    keywords: Set<String>,
    keywordColor: Color,
    stringColor: Color,
    numberColor: Color,
    commentColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(code)

        // 1. Highlight line comments (// ...)
        val commentRegex = Regex("//.*")
        for (match in commentRegex.findAll(code)) {
            addStyle(
                style = SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 2. Highlight string literals ("...")
        val stringRegex = Regex("\"(\\\\.|[^\"])*\"")
        for (match in stringRegex.findAll(code)) {
            addStyle(
                style = SpanStyle(color = stringColor, fontWeight = FontWeight.Normal),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 3. Highlight numbers
        val numberRegex = Regex("\\b\\d+(\\.\\d+)?([fFL])?\\b")
        for (match in numberRegex.findAll(code)) {
            addStyle(
                style = SpanStyle(color = numberColor),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 4. Highlight keywords
        val wordRegex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
        for (match in wordRegex.findAll(code)) {
            if (keywords.contains(match.value)) {
                addStyle(
                    style = SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
    }
}
