package dev.agam.learn.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.di.LocalAppContainer
import dev.agam.learn.model.Exercise
import dev.agam.learn.ui.components.CodeBlock
import dev.agam.learn.ui.lesson.components.ClozeCard
import dev.agam.learn.ui.lesson.components.AnimatedLessonFeedbackBanner
import dev.agam.learn.ui.lesson.components.LessonExplanation
import dev.agam.learn.ui.lesson.components.LessonSummary
import dev.agam.learn.ui.lesson.components.MultipleChoiceCard
import dev.agam.learn.ui.lesson.components.PredictOutputCard

/**
 * Full-screen lesson player supporting introduction, exercise steps, feedback, and summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    lessonId: String,
    onNavigateBack: () -> Unit,
    onOpenExercise: (route: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LessonViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LessonViewModel.provideFactory(LocalAppContainer.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(lessonId) {
        viewModel.loadLesson(lessonId)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (uiState.step !is LessonStep.Completed) {
                TopAppBar(
                    title = {
                        if (uiState.step is LessonStep.ExercisePlaying) {
                            LinearProgressIndicator(
                                progress = { uiState.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            Text(
                                text = uiState.lessonTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit lesson"
                            )
                        }
                    },
                    actions = {
                        if (uiState.step is LessonStep.ExercisePlaying) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "XP",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "+${uiState.xpEarnedInLesson}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val step = uiState.step) {
                is LessonStep.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is LessonStep.Explanation -> {
                    LessonExplanation(
                        lessonTitle = uiState.lessonTitle,
                        chapterTitle = uiState.chapterTitle,
                        explanation = step.text,
                        onStartPractice = viewModel::onStartPractice
                    )
                }

                is LessonStep.ExercisePlaying -> {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(scrollState)
                                .padding(top = 12.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (val exercise = step.exercise) {
                                is Exercise.MultipleChoice -> {
                                    MultipleChoiceCard(
                                        exercise = exercise,
                                        selectedOptionIndex = uiState.selectedOptionIndex,
                                        onOptionSelected = viewModel::onOptionSelected,
                                        isAnswerChecked = uiState.isAnswerChecked,
                                        isCorrect = uiState.isAnswerCorrect,
                                        languageSupport = viewModel.languageSupport
                                    )
                                }

                                is Exercise.Cloze -> {
                                    ClozeCard(
                                        exercise = exercise,
                                        answers = uiState.clozeAnswers,
                                        onAnswerChange = viewModel::onClozeAnswerChanged,
                                        isAnswerChecked = uiState.isAnswerChecked,
                                        isCorrect = uiState.isAnswerCorrect,
                                        languageSupport = viewModel.languageSupport
                                    )
                                }

                                is Exercise.PredictOutput -> {
                                    PredictOutputCard(
                                        exercise = exercise,
                                        userOutput = uiState.predictOutputText,
                                        onOutputChange = viewModel::onPredictOutputChanged,
                                        isAnswerChecked = uiState.isAnswerChecked,
                                        isCorrect = uiState.isAnswerCorrect,
                                        languageSupport = viewModel.languageSupport
                                    )
                                }

                                is Exercise.WriteFunction -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = exercise.prompt,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        CodeBlock(code = exercise.starterCode)
                                        Text(
                                            text = "Interactive coding exercise. Complete this drill in the Code Studio.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = { viewModel.routeForExercise(exercise)?.let(onOpenExercise) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Open in Code Studio")
                                        }
                                    }
                                }

                                is Exercise.MultiPartProblem -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = exercise.prompt,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = exercise.story,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Button(
                                            onClick = { viewModel.routeForExercise(exercise)?.let(onOpenExercise) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Open Problem Studio")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom action area: Check button when not checked, or animated feedback banner when checked.
                    // Studio exercises have no Check button: their result comes back from the studio screen.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        if (!uiState.isAnswerChecked && !uiState.isStudioExercise) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background,
                                shadowElevation = 6.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Button(
                                        onClick = viewModel::onCheckAnswer,
                                        enabled = uiState.canCheckAnswer,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Text(
                                            text = "Check",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (uiState.canCheckAnswer) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedLessonFeedbackBanner(
                            visible = uiState.isAnswerChecked,
                            feedbackKey = (uiState.step as? LessonStep.ExercisePlaying)
                                ?.let { "${it.exerciseIndex}:${it.exercise.id}" }
                                .orEmpty(),
                            isCorrect = uiState.isAnswerCorrect == true,
                            explanation = uiState.currentExplanation,
                            onContinue = viewModel::onContinueNext,
                            isAiAvailable = uiState.isAiAvailable,
                            isInterviewMode = uiState.isInterviewMode,
                            hasApiKey = uiState.hasApiKey,
                            onMoreLikeThis = viewModel::requestMoreLikeThis
                        )
                    }
                }

                is LessonStep.Completed -> {
                    LessonSummary(
                        lessonTitle = uiState.lessonTitle,
                        totalXp = step.totalXp,
                        correctCount = step.correctCount,
                        totalCount = step.totalCount,
                        onFinish = onNavigateBack
                    )
                }
            }

            if (uiState.aiPracticeState.isVisible) {
                AiPracticeSheet(
                    state = uiState.aiPracticeState,
                    onOptionSelected = viewModel::onAiPracticeOptionSelected,
                    onClozeAnswerChange = viewModel::onAiPracticeClozeAnswerChanged,
                    onCheckAnswer = viewModel::checkAiPracticeAnswer,
                    onNextCard = viewModel::nextAiPracticeCard,
                    onDismiss = viewModel::dismissAiPractice
                )
            }
        }
    }
}

/**
 * AI practice bottom sheet displaying generated cards labeled "AI-generated, unverified".
 * Awards no XP, never enters review, and is never saved to course content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPracticeSheet(
    state: AiPracticeState,
    onOptionSelected: (Int) -> Unit,
    onClozeAnswerChange: (String, String) -> Unit,
    onCheckAnswer: () -> Unit,
    onNextCard: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with unverified label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close practice sheet"
                    )
                }
            }

            if (state.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Generating practice cards...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (state.currentCard != null) {
                val card = state.currentCard!!
                Text(
                    text = "Practice Card ${state.currentCardIndex + 1} of ${state.cards.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                when (card) {
                    is Exercise.MultipleChoice -> {
                        Text(
                            text = card.prompt,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val cardCode = card.code
                        if (cardCode != null) {
                            CodeBlock(code = cardCode)
                        }
                        card.options.forEachIndexed { index, option ->
                            val isSelected = state.selectedOptionIndex == index
                            val isCorrect = index == card.correctIndex
                            val borderColor = when {
                                state.isAnswerChecked && isCorrect -> Color(0xFF2E7D32)
                                state.isAnswerChecked && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            val bgColor = when {
                                state.isAnswerChecked && isCorrect -> Color(0xFFE8F5E9)
                                state.isAnswerChecked && isSelected && !isCorrect -> Color(0xFFFFEBEE)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .background(bgColor, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !state.isAnswerChecked) { onOptionSelected(index) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { if (!state.isAnswerChecked) onOptionSelected(index) },
                                    enabled = !state.isAnswerChecked
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (state.isAnswerChecked && isCorrect) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Correct",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else if (state.isAnswerChecked && isSelected && !isCorrect) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Incorrect",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    is Exercise.Cloze -> {
                        Text(
                            text = card.prompt,
                            style = MaterialTheme.typography.titleMedium
                        )
                        CodeBlock(code = card.template)
                        card.blanks.forEach { blank ->
                            val currentVal = state.clozeAnswers[blank.placeholder].orEmpty()
                            val isBlankCorrect = blank.accepted.any {
                                if (blank.caseSensitive) it == currentVal.trim()
                                else it.equals(currentVal.trim(), ignoreCase = true)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${blank.placeholder}:",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.width(60.dp)
                                )
                                OutlinedTextField(
                                    value = currentVal,
                                    onValueChange = { onClozeAnswerChange(blank.placeholder, it) },
                                    enabled = !state.isAnswerChecked,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (state.isAnswerChecked) {
                                Text(
                                    text = if (isBlankCorrect) "✓ Correct" else "✗ Accepted: ${blank.accepted.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBlankCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 64.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }
                    else -> {}
                }

                if (state.isAnswerChecked) {
                    if (state.explanation.isNotBlank()) {
                        Text(
                            text = state.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onNextCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(if (state.currentCardIndex + 1 < state.cards.size) "Next Card" else "Done")
                    }
                } else {
                    Button(
                        onClick = onCheckAnswer,
                        enabled = state.canCheckAnswer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Check")
                    }
                }
            }
        }
    }
}
