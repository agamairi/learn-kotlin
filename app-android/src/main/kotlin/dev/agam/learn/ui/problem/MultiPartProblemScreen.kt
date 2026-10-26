package dev.agam.learn.ui.problem

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.agam.learn.ai.AI_API_KEY_MISSING_MESSAGE
import dev.agam.learn.model.FaqItem
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.ui.code.AiDialogState
import dev.agam.learn.ui.code.AiResponseDialog
import dev.agam.learn.ui.code.AiTutorMenuButton
import dev.agam.learn.ui.code.MarkdownText
import dev.agam.learn.ui.lesson.components.CodePuzzleCard

/**
 * Stateless composable for the Multi-Part Problem screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiPartProblemScreen(
    state: MultiPartProblemUiState,
    onCodeChange: (TextFieldValue) -> Unit = {},
    onSelectPart: (Int) -> Unit = {},
    onRevealNextPart: () -> Unit = {},
    onRunClick: () -> Unit = {},
    onCheckClick: () -> Unit = {},
    onToggleInterviewMode: (Boolean) -> Unit = {},
    onToggleTimer: () -> Unit = {},
    onResetTimer: () -> Unit = {},
    onExplainMistakeClick: () -> Unit = {},
    onAskHintClick: () -> Unit = {},
    onReviewCodeClick: () -> Unit = {},
    onDismissOutcome: () -> Unit = {},
    onDismissAiDialog: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onSelectOption: ((String, String) -> Unit)? = null,
    onRetryClick: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val currentPart = state.parts.getOrNull(state.currentPartIndex)
    val currentGuided = currentPart?.guided
    val optionSelector = onSelectOption ?: state.onSelectOption
    val retryHandler = onRetryClick ?: state.onRetry

    val currentPartAnswers = state.partPuzzleAnswers[state.currentPartIndex].orEmpty()
    val isCurrentPartChecked = state.partChecked[state.currentPartIndex] == true
    val isCurrentPartPassed = state.partPassed[state.currentPartIndex] == true
    val currentPartFeedback = state.partFeedback[state.currentPartIndex].orEmpty()

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

            // Header: Timer & Interview Mode Toggle
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProblemTimer(
                    secondsRemaining = state.timerSeconds,
                    isRunning = state.isTimerRunning,
                    onToggleRunning = onToggleTimer,
                    onReset = onResetTimer
                )

                Row(
                    modifier = Modifier.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Interview Mode",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = state.isInterviewMode,
                        onCheckedChange = onToggleInterviewMode,
                        enabled = !state.isTimerRunning
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Problem Story Card: Build the code
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = state.prompt.ifBlank { "Build the code" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MarkdownText(
                        markdown = state.story,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // FAQ Expandable Section
            if (state.faq.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FaqSection(
                    faqItems = state.faq,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Part Navigation Chips
            Text(
                text = "Parts (${state.unlockedPartCount} of ${state.parts.size} unlocked)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.parts.forEachIndexed { index, _ ->
                    val isUnlocked = index < state.unlockedPartCount
                    val isSelected = index == state.currentPartIndex

                    ElevatedFilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isUnlocked) onSelectPart(index)
                        },
                        label = {
                            Text(
                                text = if (isUnlocked) "Part ${index + 1}" else "🔒 Part ${index + 1}"
                            )
                        },
                        enabled = isUnlocked,
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current Part Prompt
            if (currentPart != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Part ${state.currentPartIndex + 1} Prompt",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MarkdownText(
                            markdown = currentPart.prompt,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current Part Code Puzzle or safe local unavailable message
            if (currentGuided != null) {
                CodePuzzleCard(
                    puzzle = currentGuided,
                    answers = currentPartAnswers,
                    onAnswerChange = optionSelector,
                    isAnswerChecked = isCurrentPartChecked,
                    feedback = currentPartFeedback,
                    modifier = Modifier.fillMaxWidth(),
                    isInteractionLocked = state.isChecking || state.recordError != null
                )

                Spacer(modifier = Modifier.height(14.dp))

                val allBlanksAnswered = currentGuided.blanks.isNotEmpty() && currentGuided.blanks.all {
                    val answer = currentPartAnswers[it.placeholder]
                    !answer.isNullOrBlank() && it.options.isNotEmpty() && answer in it.options
                }

                // Action Buttons
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Check Part Button
                    if (!isCurrentPartChecked) {
                        Button(
                            onClick = onCheckClick,
                            enabled = allBlanksAnswered && !state.isChecking,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (state.recordError == null) "✓ Check Part" else "Retry saving")
                        }
                    } else if (!state.isChecking && !isCurrentPartPassed) {
                        OutlinedButton(
                            onClick = retryHandler,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Try again")
                        }
                    }

                    // Reveal Next Part Button
                    val hasNextPart = state.currentPartIndex + 1 < state.parts.size
                    val nextPartLocked = state.unlockedPartCount <= state.currentPartIndex + 1
                    if (hasNextPart && nextPartLocked) {
                        Button(
                            onClick = onRevealNextPart,
                            enabled = isCurrentPartPassed,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("🔓 Reveal Part ${state.currentPartIndex + 2}")
                        }
                    } else if (!hasNextPart && isCurrentPartPassed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🎉 All parts completed!",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (onFinish != null && state.allPartsPassed) {
                                Button(
                                    onClick = onFinish,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Complete & Return to Lesson")
                                }
                            }
                        }
                    }

                    // AI Tutor Menu (hidden in interview mode; disabled with a hint until a key is saved)
                    if (state.isAiAvailable && !state.isInterviewMode && !state.isTimerRunning) {
                        AiTutorMenuButton(
                            onExplainMistake = onExplainMistakeClick,
                            onAskHint = onAskHintClick,
                            onReviewCode = onReviewCodeClick,
                            enabled = !state.isChecking && state.hasApiKey
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
            } else {
                // Safe local unavailable message when guided puzzle data is absent
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
                            text = "Offline code puzzle is not available for this part.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.isAiAvailable && !state.isInterviewMode && !state.isTimerRunning && !state.hasApiKey) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = AI_API_KEY_MISSING_MESSAGE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // AI Dialog
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
private fun MultiPartProblemScreenPreview() {
    MaterialTheme {
        MultiPartProblemScreen(
            state = MultiPartProblemUiState(
                prompt = "Ledger Processing System",
                story = "You are designing an accounting ledger to process transaction records safely and verify account balances.",
                parts = listOf(
                    ProblemPart(
                        id = "part1",
                        prompt = "Parse lines into transaction objects.",
                        starterCode = "fun parseTransactions(lines: List<String>): List<Transaction> = TODO()",
                        hiddenTests = "",
                        referenceSolution = "",
                        expected = "Parsed transaction list."
                    )
                ),
                faq = listOf(
                    FaqItem("Are IDs unique?", "Yes, IDs are guaranteed to be unique.")
                ),
                currentPartIndex = 0,
                unlockedPartCount = 1,
                isInterviewMode = false,
                isAiAvailable = true,
                hasApiKey = true
            )
        )
    }
}
