package dev.agam.learn.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agam.learn.di.LocalAppContainer
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ReviewCard
import dev.agam.learn.ui.experience.LocalLearningFeedback
import dev.agam.learn.ui.code.MarkdownText
import dev.agam.learn.ui.components.BrandArtwork
import dev.agam.learn.ui.components.BrandAsset
import dev.agam.learn.ui.theme.extendedColors
import kotlinx.coroutines.flow.collect

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
    val isLoading: Boolean = false,
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

@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ReviewViewModel.provideFactory(LocalAppContainer.current)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val learningFeedback = LocalLearningFeedback.current
    LaunchedEffect(viewModel, learningFeedback) {
        viewModel.feedbackEvents.collect { correct ->
            learningFeedback.onAnswer(correct)
        }
    }
    ReviewScreen(
        state = state,
        onStartSession = viewModel::startSession,
        onSelectOption = viewModel::selectOption,
        onClozeAnswerChange = viewModel::onClozeAnswerChange,
        onTextAnswerChange = viewModel::onTextAnswerChange,
        onCheckAnswer = viewModel::checkAnswer,
        onNextCard = viewModel::nextCard,
        onExitSession = {
            if (state.isInSession) {
                viewModel.exitSession()
            } else {
                onNavigateBack()
            }
        },
        modifier = modifier
    )
}

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
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
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
        text = "Review",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(modifier = Modifier.height(12.dp))

    val dueCount = state.dueCards.size
    val dueByBox = state.dueCards
        .groupingBy { it.box.coerceIn(1, 5) }
        .eachCount()
    val rotationCount = (1..5).sumOf { state.boxCounts[it] ?: 0 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SPACED REPETITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (dueCount == 0) "All caught up" else "$dueCount ${if (dueCount == 1) "card" else "cards"} ready for review",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                BrandArtwork(
                    asset = if (dueCount == 0) BrandAsset.ReviewDone else BrandAsset.MascotThink,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(6.dp),
                    contentDescription = null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (dueCount == 0) {
                    "No cards are due today. Cards return when their next review date arrives."
                } else {
                    "Review due cards to strengthen recall. Correct answers move forward; missed cards return to Box 1."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartSession,
                enabled = dueCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (dueCount > 0) "Start review · $dueCount ${if (dueCount == 1) "card" else "cards"}" else "No cards due today",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Memory strength",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (rotationCount == 0) "No cards in the review rotation yet." else "$rotationCount ${if (rotationCount == 1) "card" else "cards"} in the review rotation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 304.dp) 5 else 3
                val spacing = 8.dp
                val itemWidth = (maxWidth - spacing * (columns - 1)) / columns
                val intervals = listOf("1 day", "2 days", "4 days", "8 days", "16 days")

                (1..5).toList().chunked(columns).forEachIndexed { rowIndex, boxes ->
                    if (rowIndex > 0) Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        boxes.forEach { box ->
                            val count = state.boxCounts[box] ?: 0
                            val dueInBox = dueByBox[box] ?: 0
                            Card(
                                modifier = Modifier.width(itemWidth),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (dueInBox > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (dueInBox > 0)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Box $box",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = intervals[box - 1],
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (dueInBox > 0) {
                                        Text(
                                            text = "$dueInBox due",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "How review works",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cards return after 1, 2, 4, 8, then 16 days as they move through the boxes. A missed card returns to Box 1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(fontSize = (MaterialTheme.typography.labelLarge.fontSize.value + 4).sp)
                ) {
                    append("📦")
                }
                append(" Leitner Box ${currentItem.card.box}")
            },
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
                            color = Color(0xFFE0E0E0),
                            softWrap = true,
                            modifier = Modifier.fillMaxWidth()
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
                            color = if (state.isAnswerCorrect) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.error,
                            softWrap = true,
                            modifier = Modifier.fillMaxWidth()
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
        val extendedColors = MaterialTheme.extendedColors
        val bannerBg = if (state.isAnswerCorrect) extendedColors.successContainer else MaterialTheme.colorScheme.errorContainer
        val bannerColor = if (state.isAnswerCorrect) extendedColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer

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
                Text(
                    text = buildAnnotatedString {
                        if (state.currentCardIndex + 1 < state.sessionItems.size) {
                            append("Next Card →")
                        } else {
                            append("Finish Review ")
                            withStyle(
                                SpanStyle(fontSize = (MaterialTheme.typography.labelLarge.fontSize.value + 4).sp)
                            ) {
                                append("🎉")
                            }
                        }
                    }
                )
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
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandArtwork(
                    asset = BrandAsset.LessonTrophy,
                    modifier = Modifier.size(72.dp),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Review complete",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "You've reviewed all due cards for this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cards reviewed",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("${state.totalAnswered}", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Correct answers",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("${state.totalCorrect}", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "XP earned",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+${state.xpEarned} XP",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onBackToQueue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back to review")
                }
            }
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
