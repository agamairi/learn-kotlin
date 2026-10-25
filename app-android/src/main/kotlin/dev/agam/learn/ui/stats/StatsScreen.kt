package dev.agam.learn.ui.stats

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agam.learn.di.LocalAppContainer
import dev.agam.learn.ui.components.BrandArtwork
import dev.agam.learn.ui.components.BrandAsset

/** State for the Stats screen. */
data class StatsUiState(
    val totalXp: Int = 0,
    val totalExercisesCompleted: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val heldStreakFreezes: Int = 0,
    val dailyGoalXp: Int = 50,
    val recentXpHistory: List<DailyXp> = emptyList(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val chapterAccuracies: List<ChapterAccuracy> = emptyList(),
    val isLoading: Boolean = false
)

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = StatsViewModel.provideFactory(LocalAppContainer.current)
    )
) {
    val state by viewModel.uiState.collectAsState()
    StatsScreen(state = state, modifier = modifier)
}

/** Progress overview backed by the learner's recorded XP, streak, and exercise results. */
@Composable
fun StatsScreen(
    state: StatsUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val hasLoadedStats = state.totalXp != 0 ||
        state.totalExercisesCompleted != 0 ||
        state.currentStreak != 0 ||
        state.longestStreak != 0 ||
        state.recentXpHistory.isNotEmpty() ||
        state.calendarDays.isNotEmpty() ||
        state.chapterAccuracies.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Your progress",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Practice, consistency, and chapter accuracy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.isLoading && !hasLoadedStats) {
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Loading your progress"
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Loading your progress…",
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(Modifier.height(18.dp))
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.End),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                KodaEncouragement(
                    currentStreak = state.currentStreak,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                MetricStrip(state = state, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(20.dp))
                XpBarChart(
                    history = state.recentXpHistory,
                    dailyGoal = state.dailyGoalXp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                StreakCalendar(
                    currentStreak = state.currentStreak,
                    longestStreak = state.longestStreak,
                    heldFreezes = state.heldStreakFreezes,
                    days = state.calendarDays,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                ChapterAccuracyList(
                    accuracies = state.chapterAccuracies,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun KodaEncouragement(
    currentStreak: Int,
    modifier: Modifier = Modifier
) {
    val message = if (currentStreak > 0) {
        "You're on a $currentStreak-day streak. Keep your rhythm going."
    } else {
        "A little practice at a time builds a steady habit."
    }

    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(14.dp)
        ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "A note from Koda",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            BrandArtwork(
                asset = BrandAsset.MascotThink,
                modifier = Modifier.size(52.dp),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun MetricStrip(
    state: StatsUiState,
    modifier: Modifier = Modifier
) {
    val metrics = listOf(
        Metric("Total XP", state.totalXp.toString(), "earned"),
        Metric("Streak", state.currentStreak.toString(), if (state.currentStreak == 1) "day" else "days"),
        Metric("Exercises", state.totalExercisesCompleted.toString(), "completed")
    )
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            shape = shape
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useVerticalLayout = maxWidth < 360.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            if (useVerticalLayout) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    metrics.forEachIndexed { index, metric ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            )
                        }
                        MetricRow(metric = metric)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    metrics.forEachIndexed { index, metric ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .padding(horizontal = 10.dp)
                                    .size(width = 1.dp, height = 38.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            )
                        }
                        MetricColumn(metric = metric, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class Metric(
    val title: String,
    val value: String,
    val unit: String
)

@Composable
private fun MetricRow(metric: Metric) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${metric.title}: ${metric.value} ${metric.unit}"
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = metric.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${metric.value} ${metric.unit}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun MetricColumn(
    metric: Metric,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${metric.title}: ${metric.value} ${metric.unit}"
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = metric.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = metric.value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = metric.unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
