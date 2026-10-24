package dev.agam.learn.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.agam.learn.ai.AI_API_KEY_MISSING_MESSAGE
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.TestFailure

/**
 * UI State for the Code Exercise Screen.
 */
data class CodeExerciseUiState(
    val exerciseId: String = "",
    val prompt: String = "",
    val code: TextFieldValue = TextFieldValue(""),
    val starterCode: String = "",
    val hints: List<String> = emptyList(),
    val revealedHintsCount: Int = 0,
    val isRunning: Boolean = false,
    val isChecking: Boolean = false,
    val outcome: RunOutcome? = null,
    val isAiAvailable: Boolean = false,
    val isInterviewMode: Boolean = false,
    val hasApiKey: Boolean = false,
    val aiDialogState: AiDialogState? = null
)

/**
 * Stateless composable for the Code Exercise screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodeExerciseScreen(
    state: CodeExerciseUiState,
    onCodeChange: (TextFieldValue) -> Unit,
    onRunClick: () -> Unit,
    onCheckClick: () -> Unit,
    onRevealHintClick: () -> Unit,
    onExplainMistakeClick: () -> Unit,
    onAskHintClick: () -> Unit,
    onReviewCodeClick: () -> Unit,
    onDismissOutcome: () -> Unit,
    onDismissAiDialog: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            if (onNavigateBack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = "Code Drill",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Prompt Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Exercise",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MarkdownText(
                        markdown = state.prompt,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Hints Section
            if (state.hints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val hintsRemaining = state.hints.size - state.revealedHintsCount
                    TextButton(
                        onClick = onRevealHintClick,
                        enabled = hintsRemaining > 0
                    ) {
                        Text(
                            text = if (hintsRemaining > 0) {
                                "💡 Hint (${state.revealedHintsCount}/${state.hints.size})"
                            } else {
                                "💡 All hints revealed (${state.hints.size})"
                            }
                        )
                    }
                }

                if (state.revealedHintsCount > 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            state.hints.take(state.revealedHintsCount).forEachIndexed { index, hint ->
                                Text(
                                    text = "Hint ${index + 1}: $hint",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Code Editor
            CodeEditor(
                value = state.code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Run Button
                OutlinedButton(
                    onClick = onRunClick,
                    enabled = !state.isRunning && !state.isChecking,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Running...")
                    } else {
                        Text("▶ Run")
                    }
                }

                // Check Button
                Button(
                    onClick = onCheckClick,
                    enabled = !state.isRunning && !state.isChecking,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (state.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Checking...")
                    } else {
                        Text("✓ Check")
                    }
                }

                // AI Tutor Menu (hidden in interview mode; disabled with a hint until a key is saved)
                if (state.isAiAvailable && !state.isInterviewMode) {
                    AiTutorMenuButton(
                        onExplainMistake = onExplainMistakeClick,
                        onAskHint = onAskHintClick,
                        onReviewCode = onReviewCodeClick,
                        enabled = !state.isRunning && !state.isChecking && state.hasApiKey
                    )
                }
            }
            if (state.isAiAvailable && !state.isInterviewMode && !state.hasApiKey) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = AI_API_KEY_MISSING_MESSAGE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Results Panel
            if (state.outcome != null) {
                Spacer(modifier = Modifier.height(16.dp))
                RunOutcomeView(
                    outcome = state.outcome,
                    onDismiss = onDismissOutcome
                )
                if (state.outcome is RunOutcome.Passed && onFinish != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Complete & Return to Lesson")
                    }
                }
            }
        }

        // AI Tutor response dialog
        if (state.aiDialogState != null) {
            AiResponseDialog(
                state = state.aiDialogState,
                onDismiss = onDismissAiDialog
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CodeExerciseScreenPreview() {
    MaterialTheme {
        CodeExerciseScreen(
            state = CodeExerciseUiState(
                prompt = "Write a function `sum(a: Int, b: Int): Int` that returns the sum of `a` and `b`.",
                code = TextFieldValue("fun sum(a: Int, b: Int): Int {\n    return a + b\n}"),
                hints = listOf("Use the + operator.", "Return expression directly."),
                revealedHintsCount = 1,
                isAiAvailable = true,
                isInterviewMode = false,
                hasApiKey = true
            ),
            onCodeChange = {},
            onRunClick = {},
            onCheckClick = {},
            onRevealHintClick = {},
            onExplainMistakeClick = {},
            onAskHintClick = {},
            onReviewCodeClick = {},
            onDismissOutcome = {},
            onDismissAiDialog = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CodeExerciseScreenFailedPreview() {
    MaterialTheme {
        CodeExerciseScreen(
            state = CodeExerciseUiState(
                prompt = "Return each merchant's total cents, sorted by merchant.",
                code = TextFieldValue("fun totals(sales: List<Sale>): List<Pair<String, Long>> = emptyList()"),
                outcome = RunOutcome.TestsFailed(
                    failures = listOf(
                        TestFailure("testExample", "expected:<[acme to 3550]> but was:<[]>")
                    ),
                    stdout = ""
                ),
                isAiAvailable = true,
                isInterviewMode = false,
                hasApiKey = true
            ),
            onCodeChange = {},
            onRunClick = {},
            onCheckClick = {},
            onRevealHintClick = {},
            onExplainMistakeClick = {},
            onAskHintClick = {},
            onReviewCodeClick = {},
            onDismissOutcome = {},
            onDismissAiDialog = {}
        )
    }
}
