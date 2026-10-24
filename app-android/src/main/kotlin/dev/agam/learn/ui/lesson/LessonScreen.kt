package dev.agam.learn.ui.lesson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.di.LocalAppContainer
import dev.agam.learn.model.Exercise
import dev.agam.learn.ui.components.CodeBlock
import dev.agam.learn.ui.lesson.components.ClozeCard
import dev.agam.learn.ui.lesson.components.FeedbackBanner
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
                                    }
                                }
                            }
                        }
                    }

                    // Bottom action area: Check button when not checked, or animated feedback banner when checked
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        if (!uiState.isAnswerChecked) {
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

                        AnimatedVisibility(
                            visible = uiState.isAnswerChecked,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            FeedbackBanner(
                                isCorrect = uiState.isAnswerCorrect == true,
                                explanation = uiState.currentExplanation,
                                onContinue = viewModel::onContinueNext
                            )
                        }
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
        }
    }
}
