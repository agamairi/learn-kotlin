package dev.agam.learn.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Inserts a string at the current cursor position in a [TextFieldValue], replacing any selected text.
 */
fun insertTextAtCursor(current: TextFieldValue, textToInsert: String): TextFieldValue {
    val selection = current.selection
    val start = selection.min
    val end = selection.max
    val currentText = current.text
    val newText = currentText.substring(0, start) + textToInsert + currentText.substring(end)
    val newCursorPos = start + textToInsert.length
    return current.copy(
        text = newText,
        selection = TextRange(newCursorPos)
    )
}

/**
 * Standard symbol set for the quick symbol entry bar.
 */
val CODE_SYMBOL_LIST: List<String> = listOf(
    "{", "}", "(", ")", "<", ">", "=", "\"", ".", ",", ":", "->", "_"
)

/**
 * Stateless code editor featuring line numbers, monospace font, syntax highlighting,
 * and a quick symbol insertion bar.
 */
@Composable
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    keywords: Set<String> = KeywordVisualTransformation.DEFAULT_KEYWORDS
) {
    val keywordTransformation = remember(keywords) {
        KeywordVisualTransformation(keywords = keywords)
    }

    val lineCount = remember(value.text) {
        maxOf(1, value.text.count { it == '\n' } + 1)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Code Editor Label Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Code Editor",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Symbol bar
        SymbolBar(
            onSymbolClick = { symbol ->
                onValueChange(insertTextAtCursor(value, symbol))
            },
            onTabClick = {
                onValueChange(insertTextAtCursor(value, "    "))
            },
            enabled = !readOnly
        )

        // Text editor area with line numbers
        val editorScrollState = rememberScrollState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 360.dp)
                .height(IntrinsicSize.Min)
                .padding(vertical = 8.dp)
                .verticalScroll(editorScrollState)
        ) {
            // Line numbers column
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .padding(end = 6.dp)
            ) {
                for (line in 1..lineCount) {
                    Text(
                        text = line.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp)
            )

            // BasicTextField with monospace font and keyword highlighting
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = keywordTransformation,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = "Code editor" }
            )
        }
    }
}

/**
 * Scrollable row of quick-access symbol chips and a Tab button.
 */
@Composable
fun SymbolBar(
    onSymbolClick: (String) -> Unit,
    onTabClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbols: List<String> = CODE_SYMBOL_LIST,
    enabled: Boolean = true
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Tab key chip
        AssistChip(
            onClick = onTabClick,
            enabled = enabled,
            label = {
                Text(
                    text = "Tab",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .padding(end = 4.dp)
                .defaultMinSize(minHeight = 48.dp)
        )

        symbols.forEach { symbol ->
            AssistChip(
                onClick = { onSymbolClick(symbol) },
                enabled = enabled,
                label = {
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .defaultMinSize(minHeight = 48.dp)
            )
        }
    }
}

@Preview
@Composable
private fun CodeEditorPreview() {
    MaterialTheme {
        CodeEditor(
            value = TextFieldValue(
                """
                fun sum(a: Int, b: Int): Int {
                    // Calculate sum
                    return a + b
                }
                """.trimIndent()
            ),
            onValueChange = {}
        )
    }
}
