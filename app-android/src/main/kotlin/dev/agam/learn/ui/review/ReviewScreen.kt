package dev.agam.learn.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ReviewCard
import dev.agam.learn.ui.code.MarkdownText

/**
 * An item in the active review session, bundling the Leitner card with its corresponding exercise.
 */
data class ReviewSessionItem(
    val card: ReviewCard,
    val exercise: Exercise
)

/**
 * State of the Leitner Review screen.
 */
data class ReviewUiState(
    val dueCards: List<ReviewCard> = emptyList(),
    val boxCounts: Map<Int, Int> = mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0),
    val isInSession: Boolean = false,
    val isSessionComplete: Boolean = false,
    val sessionItems: List<ReviewSessionItem> = emptyList(),
    val currentCardIndex: Int = 0,
    val selectedOptionIndex: Int? = null,
    val clozeAnswers: Map<String, String> = emptyMap(),
    val textAnswer: String = "",
    val isAnswerChecked: Boolean = false,
    val isAnswerCorrect: Boolean = false,
    val updatedBox: Int? = null,
    val nextReviewDays: Int? = null,
    val totalAnswered: Int = 0,
    val totalCorrect: Int = 0,
    val xpEarned: Int = 0
)

/**
 * Stateless Leitner spaced repetition review screen.
 */
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onStartSession: () -> Unit,
    onSelectOption: (Int) -> Unit,
    onClozeAnswerChange: (String, String) -> Unit,
    onTextAnswerChange: (String) -> Unit,
    onCheckAnswer: () -> Unit,
    onNextCard: () -> Unit,
    onExitSession: () -> Unit,
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
            when {
                state.isSessionComplete -> {
                    ReviewSessionCompleteView(
                        state = state,
                        onBackToQueue = onExitSession
                    )
                }
                state.isInSession -> {
                    ActiveReviewSessionView(
                        state = state,
                        onSelectOption = onSelectOption,
                        onClozeAnswerChange = onClozeAnswerChange,
                        onTextAnswerChange = onTextAnswerChange,
                        onCheckAnswer = onCheckAnswer,
                        onNextCard = onNextCard,
                        onExitSession = onExitSession
                    )
                }
                else -> {
                    ReviewQueueOverview(
                        state = state,
                        onStartSession = onStartSession
                    )
                }
            }
        }
    }
}

/**
 * Leitner queue overview listing due cards, box distribution, and start button.
 */
@Composable
private fun ReviewQueueOverview(
    state: ReviewUiState,
    onStartSession: () -> Unit
) {
    Text(
        text = "Spaced Repetition Review",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "5-Box Leitner Spaced Repetition Queue",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Due cards count hero card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.dueCards.isNotEmpty())
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${state.dueCards.size}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (state.dueCards.isNotEmpty())
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (state.dueCards.isNotEmpty()) "Cards Due for Review Today" else "Queue is Clear!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Leitner Box Breakdown
    Text(
        text = "Leitner Box Distribution",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    val intervals = listOf("1d", "2d", "4d", "8d", "16d")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (1..5).forEach { box ->
            val count = state.boxCounts[box] ?: 0
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Box $box",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = intervals[box - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Action button
    Button(
        onClick = onStartSession,
        enabled = state.dueCards.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = if (state.dueCards.isNotEmpty()) "Start Review (${state.dueCards.size} cards)" else "No Cards Due",
            fontSize = 16.sp
        )
    }

    if (state.dueCards.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Due Items",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        state.dueCards.forEach { card ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = card.itemId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Due epoch day: ${card.dueEpochDay}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Box ${card.box}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Active review card presentation.
 */
@Composable
private fun ActiveReviewSessionView(
    state: ReviewUiState,
    onSelectOption: (Int) -> Unit,
    onClozeAnswerChange: (String, String) -> Unit,
    onTextAnswerChange: (String) -> Unit,
    onCheckAnswer: () -> Unit,
    onNextCard: () -> Unit,
    onExitSession: () -> Unit
) {
    val currentItem = state.sessionItems.getOrNull(state.currentCardIndex) ?: return
    val progress = (state.currentCardIndex + 1).toFloat() / state.sessionItems.size

    // Header: progress and box indicator
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Card ${state.currentCardIndex + 1} of ${state.sessionItems.size}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "📦 Leitner Box ${currentItem.card.box}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(modifier = Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Render exercise card
    when (val exercise = currentItem.exercise) {
        is Exercise.MultipleChoice -> {
            MultipleChoiceCard(
                exercise = exercise,
                selectedIndex = state.selectedOptionIndex,
                onSelectOption = onSelectOption,
                isAnswerChecked = state.isAnswerChecked
            )
        }
        is Exercise.Cloze -> {
            ClozeCard(
                exercise = exercise,
                answers = state.clozeAnswers,
                onAnswerChange = onClozeAnswerChange,
                isAnswerChecked = state.isAnswerChecked
            )
        }
        is Exercise.PredictOutput -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Predict Output",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MarkdownText(markdown = exercise.prompt)

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF212121), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = exercise.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFE0E0E0)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.textAnswer,
                        onValueChange = onTextAnswerChange,
                        label = { Text("What will this print?") },
                        enabled = !state.isAnswerChecked,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.isAnswerChecked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.isAnswerCorrect) "✓ Correct" else "Expected output:\n${exercise.expectedOutput}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isAnswerCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        else -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = exercise.prompt)
                }
            }
        }
    }

    // Feedback banner
    if (state.isAnswerChecked) {
        Spacer(modifier = Modifier.height(14.dp))
        val bannerBg = if (state.isAnswerCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        val bannerColor = if (state.isAnswerCorrect) Color(0xFF1B5E20) else Color(0xFFB71C1C)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = bannerBg)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (state.isAnswerCorrect) {
                        "✓ Correct! +10 XP"
                    } else {
                        "✗ Incorrect"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = bannerColor
                )
                Text(
                    text = if (state.isAnswerCorrect) {
                        "Promoted to Box ${state.updatedBox} (Next review in ${state.nextReviewDays} days)"
                    } else {
                        "Demoted to Box 1 (Review tomorrow)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = bannerColor
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Bottom action buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(
            onClick = onExitSession,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Quit")
        }

        if (!state.isAnswerChecked) {
            Button(
                onClick = onCheckAnswer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Check Answer")
            }
        } else {
            Button(
                onClick = onNextCard,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (state.currentCardIndex + 1 < state.sessionItems.size) "Next Card →" else "Finish Review 🎉")
            }
        }
    }
}

/**
 * Summary screen displayed at the conclusion of a review session.
 */
@Composable
private fun ReviewSessionCompleteView(
    state: ReviewUiState,
    onBackToQueue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉",
            fontSize = 48.sp
        )
        Text(
            text = "Review Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "You've reviewed all due cards for this session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cards Reviewed:")
                    Text("${state.totalAnswered}", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Correct Answers:")
                    Text("${state.totalCorrect}", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Accuracy:")
                    val accuracy = if (state.totalAnswered > 0) {
                        (state.totalCorrect * 100) / state.totalAnswered
                    } else 0
                    Text("$accuracy%", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("XP Earned:")
                    Text("+${state.xpEarned} XP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBackToQueue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Back to Queue")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewQueueOverviewPreview() {
    MaterialTheme {
        ReviewScreen(
            state = ReviewUiState(
                dueCards = listOf(
                    ReviewCard("card-1", 1, 100),
                    ReviewCard("card-2", 2, 100)
                ),
                boxCounts = mapOf(1 to 3, 2 to 5, 3 to 2, 4 to 1, 5 to 0)
            ),
            onStartSession = {},
            onSelectOption = {},
            onClozeAnswerChange = { _, _ -> },
            onTextAnswerChange = {},
            onCheckAnswer = {},
            onNextCard = {},
            onExitSession = {}
        )
    }
}
