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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An individual day item for the streak calendar grid.
 */
data class CalendarDay(
    val dayOfMonth: Int,
    val isActive: Boolean,
    val isToday: Boolean = false,
    /** A missed day covered by a streak freeze. */
    val isFrozen: Boolean = false
)

/**
 * Streak calendar showing streak counts and a monthly activity grid.
 */
@Composable
fun StreakCalendar(
    currentStreak: Int,
    longestStreak: Int,
    days: List<CalendarDay>,
    modifier: Modifier = Modifier,
    heldFreezes: Int = 0
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: streak numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥 $currentStreak Day Streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                }

                Text(
                    text = "Best: $longestStreak days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = freezeSummary(heldFreezes, days.any { it.isFrozen }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Day of week headers
            val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Calendar day grid (chunks of 7)
            val weeks = days.chunked(7)
            weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    week.forEach { day ->
                        DayCircle(day = day)
                    }
                    // Fill row if incomplete week
                    val missing = 7 - week.size
                    for (i in 0 until missing) {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCircle(day: CalendarDay) {
    val activeColor = Color(0xFFE65100)
    val todayBorder = if (day.isToday) MaterialTheme.colorScheme.primary else Color.Transparent

    val backgroundModifier = when {
        day.isActive -> Modifier.background(activeColor, CircleShape)
        day.isFrozen -> Modifier.background(FrozenDayColor.copy(alpha = 0.25f), CircleShape)
        else -> Modifier.background(Color.Transparent, CircleShape)
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .border(width = if (day.isToday) 2.dp else 1.dp, color = if (day.isToday) todayBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape = CircleShape)
            .clip(CircleShape)
            .then(backgroundModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${day.dayOfMonth}",
            fontSize = 11.sp,
            fontWeight = if (day.isActive || day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (day.isActive) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

private val FrozenDayColor = Color(0xFF3F7FD6)

private fun freezeSummary(heldFreezes: Int, anyFrozenDay: Boolean): String {
    val held = when (heldFreezes) {
        0 -> "No freezes held"
        1 -> "1 freeze held"
        else -> "$heldFreezes freezes held"
    }
    return if (anyFrozenDay) "$held · blue days were covered by a freeze" else held
}

@Preview
@Composable
private fun StreakCalendarPreview() {
    MaterialTheme {
        val fakeDays = (1..28).map {
            CalendarDay(
                dayOfMonth = it,
                isActive = it in listOf(2, 3, 5, 6, 7, 10, 11, 12, 13, 14, 18, 19, 20),
                isToday = it == 20
            )
        }
        StreakCalendar(
            currentStreak = 3,
            longestStreak = 5,
            days = fakeDays
        )
    }
}
