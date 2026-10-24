package dev.agam.learn.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agam.learn.di.LocalAppContainer

/**
 * State for the Stats screen.
 */
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
    StatsScreen(
        state = state,
        modifier = modifier
    )
}

/**
 * Stateless screen displaying learner stats: XP history chart, streak calendar, and per-chapter accuracy.
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
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
            Text(
                text = "Learning Stats",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your progress, XP streaks, and mastery metrics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Overview Metric Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Total XP",
                    value = "${state.totalXp}",
                    subtitle = "Points earned",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Streak",
                    value = "🔥 ${state.currentStreak}d",
                    subtitle = "Current",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Completed",
                    value = "${state.totalExercisesCompleted}",
                    subtitle = "Exercises",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. XP History Chart (Canvas)
            XpBarChart(
                history = state.recentXpHistory,
                dailyGoal = state.dailyGoalXp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Streak Calendar
            StreakCalendar(
                currentStreak = state.currentStreak,
                longestStreak = state.longestStreak,
                heldFreezes = state.heldStreakFreezes,
                days = state.calendarDays,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Per-Chapter Accuracy
            ChapterAccuracyList(
                accuracies = state.chapterAccuracies,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenPreview() {
    MaterialTheme {
        StatsScreen(
            state = StatsUiState(
                totalXp = 485,
                totalExercisesCompleted = 42,
                currentStreak = 4,
                longestStreak = 7,
                dailyGoalXp = 50,
                recentXpHistory = listOf(
                    DailyXp("Mon", 35),
                    DailyXp("Tue", 65, goalMet = true),
                    DailyXp("Wed", 50, goalMet = true),
                    DailyXp("Thu", 15),
                    DailyXp("Fri", 70, goalMet = true),
                    DailyXp("Sat", 40),
                    DailyXp("Sun", 80, goalMet = true)
                ),
                calendarDays = (1..28).map {
                    CalendarDay(it, isActive = it in listOf(3, 4, 7, 8, 9, 10, 14, 15, 16, 21, 22), isToday = it == 22)
                },
                chapterAccuracies = listOf(
                    ChapterAccuracy("ch01", "Syntax Basics", 10, 9, 90),
                    ChapterAccuracy("ch02", "Strings & Text", 12, 10, 83),
                    ChapterAccuracy("ch03", "Collections", 15, 12, 80)
                )
            )
        )
    }
}
