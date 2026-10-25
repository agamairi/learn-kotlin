package dev.agam.learn.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.di.LocalAppContainer
import dev.agam.learn.ui.components.BrandArtwork
import dev.agam.learn.ui.components.BrandAsset
import dev.agam.learn.ui.experience.LocalReducedMotion
import dev.agam.learn.ui.theme.extendedColors

/** Home dashboard with a roomy next-lesson card, daily goal, streak, and review shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onContinueLesson: (lessonId: String) -> Unit = {},
    onOpenPath: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = HomeViewModel.provideFactory(LocalAppContainer.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val extendedColors = MaterialTheme.extendedColors
    val reducedMotion = LocalReducedMotion.current
    val goalProgress by animateFloatAsState(
        targetValue = uiState.goalProgressRatio.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 550),
        label = "daily-goal-progress"
    )

    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "learn-kotlin",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BrandArtwork(
                                asset = BrandAsset.StreakFlame,
                                modifier = Modifier.size(20.dp),
                                contentDescription = null
                            )
                            Text(
                                text = streakLabel(uiState.streakDays, uiState.heldStreakFreezes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val continueInfo = uiState.continueLesson
                if (continueInfo != null) {
                    item(key = "continue-lesson") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "UP NEXT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                    Text(
                                        text = "${continueInfo.exerciseCount} exercises",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = continueInfo.chapterTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                    )
                                    Text(
                                        text = continueInfo.lessonTitle,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    BrandArtwork(
                                        asset = if (uiState.isGoalMet) BrandAsset.MascotCelebrate else BrandAsset.MascotWave,
                                        modifier = Modifier.size(58.dp),
                                        contentDescription = null
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = if (uiState.isGoalMet) "Goal reached — nice work!" else "Koda is ready when you are.",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "A little practice goes a long way.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onContinueLesson(continueInfo.lessonId) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 54.dp)
                                ) {
                                    Text(
                                        text = "Continue lesson",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "daily-goal") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BrandArtwork(
                                    asset = BrandAsset.XpSpark,
                                    modifier = Modifier.size(34.dp),
                                    contentDescription = null
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Daily goal",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (uiState.isGoalMet) "You've reached today's target" else "${(uiState.dailyGoalXp - uiState.todayXp).coerceAtLeast(0)} XP to go",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (uiState.isGoalMet) extendedColors.success else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (uiState.isGoalMet) extendedColors.successContainer else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${uiState.todayXp}/${uiState.dailyGoalXp} XP",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.isGoalMet) extendedColors.success else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { goalProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (uiState.isGoalMet) extendedColors.success else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }

                item(key = "quick-shortcuts") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShortcutCard(
                            modifier = Modifier.weight(1f),
                            onClick = null,
                            icon = if (uiState.streakDays >= 5) BrandAsset.MascotFly else BrandAsset.StreakFlame,
                            headline = uiState.streakDays.toString(),
                            label = "day streak",
                            detail = streakCardDetail(
                                isActiveToday = uiState.isStreakActiveToday,
                                heldFreezes = uiState.heldStreakFreezes
                            ),
                            tint = MaterialTheme.colorScheme.tertiaryContainer
                        )
                        ShortcutCard(
                            modifier = Modifier.weight(1f),
                            onClick = onOpenReview,
                            icon = BrandAsset.ReviewCards,
                            headline = uiState.reviewDueCount.toString(),
                            label = "cards due",
                            detail = "Spaced review",
                            tint = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                }

                item(key = "curriculum-shortcut") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenPath() },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Explore the learning path",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Chapters, lessons, and checkpoints",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    icon: BrandAsset,
    headline: String,
    label: String,
    detail: String,
    tint: Color
) {
    Surface(
        modifier = if (onClick == null) modifier else modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(shape = CircleShape, color = tint, modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        BrandArtwork(icon, Modifier.size(32.dp), contentDescription = null)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Top bar streak text, with held freezes when the learner has any. */
private fun streakLabel(streakDays: Int, heldFreezes: Int): String =
    if (heldFreezes > 0) "${streakDays}d streak · ❄ $heldFreezes" else "${streakDays}d streak"

private fun streakCardDetail(isActiveToday: Boolean, heldFreezes: Int): String = buildString {
    append(if (isActiveToday) "Active today" else "Practice today")
    append(" · ")
    append(
        when (heldFreezes) {
            0 -> "no freezes"
            1 -> "1 freeze"
            else -> "$heldFreezes freezes"
        }
    )
}
