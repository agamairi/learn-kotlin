package dev.agam.learn.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A recent day in the learner's goal history. */
data class CalendarDay(
    val dayOfMonth: Int,
    /** True when this date met the learner's daily XP goal. */
    val isActive: Boolean,
    val isToday: Boolean = false,
    /** A missed day covered by a streak freeze. */
    val isFrozen: Boolean = false
)

/** Recent practice consistency and streak summary. */
@Composable
fun StreakCalendar(
    currentStreak: Int,
    longestStreak: Int,
    days: List<CalendarDay>,
    modifier: Modifier = Modifier,
    heldFreezes: Int = 0
) {
    val colorScheme = MaterialTheme.colorScheme
    val goalMetCount = days.count { it.isActive }
    val frozenCount = days.count { it.isFrozen }
    val freezeCountLabel = when (heldFreezes) {
        0 -> "No freezes held"
        1 -> "1 freeze held"
        else -> "$heldFreezes freezes held"
    }
    val description = buildString {
        append("Streak consistency. Current streak: $currentStreak ${if (currentStreak == 1) "day" else "days"}. ")
        append("Longest streak: $longestStreak ${if (longestStreak == 1) "day" else "days"}. ")
        append("$goalMetCount days met the daily goal and $frozenCount days were freeze-covered in the recent history. ")
        append(freezeCountLabel)
    }
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = shape
            )
            .semantics { contentDescription = description },
        shape = shape,
        color = colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Streak consistency",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreakMetric(
                    label = "Current streak",
                    value = "$currentStreak ${if (currentStreak == 1) "day" else "days"}",
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .size(width = 1.dp, height = 34.dp)
                        .background(colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                StreakMetric(
                    label = "Longest streak",
                    value = "$longestStreak ${if (longestStreak == 1) "day" else "days"}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Freezes held",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = heldFreezes.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            Spacer(Modifier.height(12.dp))

            if (days.isEmpty()) {
                Text(
                    text = "Recent practice days will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Text(
                    text = if (days.size == 28) "Last 28 days" else "Recent practice",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(9.dp))

                // These are recent consecutive days, not a month-aligned calendar. Leaving out
                // weekday headings avoids implying a calendar alignment the state does not carry.
                days.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        week.forEach { day -> DayCircle(day = day) }
                        repeat(7 - week.size) { Spacer(Modifier.size(32.dp)) }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(
                        color = colorScheme.primaryContainer,
                        label = "Goal met"
                    )
                    if (frozenCount > 0) {
                        LegendItem(
                            color = colorScheme.secondaryContainer,
                            label = "Freeze used"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LegendItem(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayCircle(day: CalendarDay) {
    val colors = MaterialTheme.colorScheme
    val fillColor = when {
        day.isActive -> colors.primaryContainer
        day.isFrozen -> colors.secondaryContainer
        else -> colors.surface
    }
    val numberColor = when {
        day.isActive -> colors.onPrimaryContainer
        day.isFrozen -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    val dayState = when {
        day.isActive -> "daily XP goal met"
        day.isFrozen -> "covered by a streak freeze"
        else -> "daily XP goal not met"
    }
    val dayDescription = "Day ${day.dayOfMonth}, $dayState${if (day.isToday) ", today" else ""}"

    Box(
        modifier = Modifier
            .size(32.dp)
            .border(
                width = if (day.isToday) 2.dp else 1.dp,
                color = if (day.isToday) colors.primary else colors.outlineVariant.copy(alpha = 0.55f),
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(fillColor)
            .semantics { contentDescription = dayDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (day.isToday || day.isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = numberColor,
            textAlign = TextAlign.Center
        )
    }
}
