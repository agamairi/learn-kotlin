package dev.agam.learn.ui.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** XP earned on a specific day. */
data class DailyXp(
    val dayLabel: String,
    val xp: Int,
    val goalMet: Boolean = false
)

/** Recent XP history drawn as a compact, accessible bar chart. */
@Composable
fun XpBarChart(
    history: List<DailyXp>,
    dailyGoal: Int = 50,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val goal = dailyGoal.coerceAtLeast(0)
    val goalDays = history.count { it.goalMet || (goal > 0 && it.xp >= goal) }
    val chartSummary = buildString {
        append("Daily practice, ${history.size} days. ")
        if (history.isNotEmpty()) {
            append(history.joinToString(separator = "; ") { "${it.dayLabel}: ${it.xp.coerceAtLeast(0)} XP" })
            append(". ")
        }
        if (goal > 0) {
            append("Daily goal: $goal XP, met on $goalDays days.")
        } else {
            append("No daily XP goal is set.")
        }
    }
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = shape
            ),
        shape = shape,
        color = colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily practice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (goal > 0) "Goal: $goal XP per day" else "XP earned each day",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                if (goal > 0 && history.isNotEmpty()) {
                    Text(
                        text = "$goalDays / ${history.size} days",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 8.dp, top = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            if (history.isEmpty()) {
                Text(
                    text = "Your daily XP will appear here after you practice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                val maxXp = history.maxOfOrNull { it.xp.coerceAtLeast(0) } ?: 0
                val chartMax = maxOf(maxXp, goal).coerceAtLeast(1).toFloat() * 1.12f
                val chartHeight = 104.dp
                val targetLineColor = colorScheme.tertiary.copy(alpha = 0.8f)
                val belowGoalColor = colorScheme.primaryContainer
                val metGoalColor = colorScheme.primary
                val emptyDayColor = colorScheme.surfaceContainerHigh

                Box(modifier = Modifier.fillMaxWidth()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight)
                            .semantics { contentDescription = chartSummary }
                    ) {
                        val availableHeight = size.height - 3f
                        val slotWidth = size.width / history.size
                        val barWidth = (slotWidth * 0.52f).coerceIn(2f, 24.dp.toPx())

                        drawLine(
                            color = colorScheme.outlineVariant,
                            start = Offset(0f, availableHeight),
                            end = Offset(size.width, availableHeight),
                            strokeWidth = 1.dp.toPx()
                        )

                        if (goal > 0) {
                            val goalY = availableHeight - (goal / chartMax * availableHeight)
                            if (goalY in 0f..availableHeight) {
                                drawLine(
                                    color = targetLineColor,
                                    start = Offset(0f, goalY),
                                    end = Offset(size.width, goalY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                                        0f
                                    )
                                )
                            }
                        }

                        history.forEachIndexed { index, item ->
                            val xp = item.xp.coerceAtLeast(0)
                            val reachedGoal = item.goalMet || (goal > 0 && xp >= goal)
                            val barHeight = if (xp == 0) {
                                3.dp.toPx()
                            } else {
                                (xp / chartMax * availableHeight).coerceIn(3.dp.toPx(), availableHeight)
                            }
                            val x = index * slotWidth + (slotWidth - barWidth) / 2f
                            val y = availableHeight - barHeight
                            val fillColor = when {
                                xp == 0 -> emptyDayColor
                                reachedGoal -> metGoalColor
                                else -> belowGoalColor
                            }

                            drawRoundRect(
                                color = fillColor,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    history.forEach { item ->
                        val xp = item.xp.coerceAtLeast(0)
                        val reachedGoal = item.goalMet || (goal > 0 && xp >= goal)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = compactXp(xp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (reachedGoal) colorScheme.primary else colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.dayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (goal > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 2.dp)
                                .background(targetLineColor)
                        )
                        Text(
                            text = "Daily goal",
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun compactXp(xp: Int): String = when {
    xp < 1_000 -> xp.toString()
    xp % 1_000 == 0 -> "${xp / 1_000}k"
    else -> "${xp / 1_000}.${(xp % 1_000) / 100}k"
}
