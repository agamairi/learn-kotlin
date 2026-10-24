package dev.agam.learn.ui.code

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * State of the AI Tutor response dialog.
 */
data class AiDialogState(
    val title: String,
    val content: String? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false
)

/**
 * Dropdown menu button exposing the AI Tutor options:
 * - Explain my mistake
 * - Hint
 * - Review my code
 *
 * Visible only when AI is available and interview mode is disabled.
 */
@Composable
fun AiTutorMenuButton(
    onExplainMistake: () -> Unit,
    onAskHint: () -> Unit,
    onReviewCode: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "✨ Ask AI Tutor")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Explain my mistake") },
                onClick = {
                    expanded = false
                    onExplainMistake()
                }
            )
            DropdownMenuItem(
                text = { Text("Hint") },
                onClick = {
                    expanded = false
                    onAskHint()
                }
            )
            DropdownMenuItem(
                text = { Text("Review my code") },
                onClick = {
                    expanded = false
                    onReviewCode()
                }
            )
        }
    }
}

/**
 * Dialog displaying AI Tutor explanation, progressive hint, or code review.
 */
@Composable
fun AiResponseDialog(
    state: AiDialogState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!state.isLoading) onDismiss()
        },
        title = {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (state.isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Consulting AI tutor...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (state.content != null) {
                    if (state.isError) {
                        Text(
                            text = state.content,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        MarkdownText(
                            markdown = state.content,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isLoading
            ) {
                Text("Close")
            }
        }
    )
}

@Preview
@Composable
private fun AiDialogPreview() {
    MaterialTheme {
        AiResponseDialog(
            state = AiDialogState(
                title = "AI Code Review",
                content = "Great job using `groupBy`! Consider using `sumOf` instead of `map + sum` to reduce allocations.",
                isLoading = false
            ),
            onDismiss = {}
        )
    }
}
