package dev.agam.learn.ui.code

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import dev.agam.learn.model.Blank
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.ui.lesson.components.CodePuzzleCard

/**
 * Stateless composable for the Code Exercise / Code Puzzle screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodeExerciseScreen(
    state: CodeExerciseUiState,
    onCodeChange: (TextFieldValue) -> Unit = {},
    onRunClick: () -> Unit = {},
    onCheckClick: () -> Unit = {},
    onRevealHintClick: () -> Unit = {},
    onExplainMistakeClick: () -> Unit = {},
    onAskHintClick: () -> Unit = {},
    onReviewCodeClick: () -> Unit = {},
    onDismissOutcome: () -> Unit = {},
    onDismissAiDialog: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onAskHintLevelClick: ((Int) -> Unit)? = null,
    onSelectOption: ((String, String) -> Unit)? = null,
    onRetryClick: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val optionSelector = onSelectOption ?: state.onSelectOption
    val retryHandler = onRetryClick ?: state.onRetry

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
                            contentDescription = "Back",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Code puzzle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Prompt Card: Build the code
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Build the code",
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

            // Guided code puzzle or safe local unavailable message
            val guided = state.guided
            if (guided != null) {
                CodePuzzleCard(
                    puzzle = guided,
                    answers = state.puzzleAnswers,
                    onAnswerChange = optionSelector,
                    isAnswerChecked = state.isAnswerChecked,
                    feedback = state.feedback,
                    modifier = Modifier.fillMaxWidth(),
                    isInteractionLocked = state.isChecking || state.recordError != null
                )

                Spacer(modifier = Modifier.height(16.dp))

                val allBlanksAnswered = guided.blanks.isNotEmpty() && guided.blanks.all {
                    val answer = state.puzzleAnswers[it.placeholder]
                    !answer.isNullOrBlank() && it.options.isNotEmpty() && answer in it.options
                }

                // Action row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!state.isAnswerChecked) {
                        Button(
                            onClick = onCheckClick,
                            enabled = allBlanksAnswered && !state.isChecking,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (state.recordError == null) "✓ Check" else "Retry saving")
                        }
                    } else if (!state.isPassed) {
                        OutlinedButton(
                            onClick = retryHandler,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Try again")
                        }
                    }

                    // AI Tutor Menu (optional helper)
                    if (state.isAiAvailable && !state.isInterviewMode) {
                        AiTutorMenuButton(
                            onExplainMistake = onExplainMistakeClick,
                            onAskHint = onAskHintClick,
                            onReviewCode = onReviewCodeClick,
                            enabled = !state.isChecking && state.hasApiKey,
                            onAskHintLevel = onAskHintLevelClick ?: { onAskHintClick() }
                        )
                    }
                }

                state.recordError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (state.isPassed && onFinish != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Complete & Return to Lesson")
                    }
                }
            } else {
                // Safe local unavailable message when puzzle data is absent; never invoke compiler fallback
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Unavailable",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Offline code puzzle is not available for this exercise.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.isAiAvailable && !state.isInterviewMode && !state.hasApiKey) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = AI_API_KEY_MISSING_MESSAGE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                guided = CodePuzzle(
                    template = "fun sum(a: Int, b: Int): Int = ____1",
                    blanks = listOf(
                        Blank(
                            placeholder = "____1",
                            accepted = listOf("a + b"),
                            options = listOf("a + b", "a - b", "a * b", "a / b")
                        )
                    )
                ),
                puzzleAnswers = mapOf("____1" to "a + b"),
                hints = listOf("Use the + operator.", "Return expression directly."),
                revealedHintsCount = 1,
                isAiAvailable = true,
                isInterviewMode = false,
                hasApiKey = true
            )
        )
    }
}
