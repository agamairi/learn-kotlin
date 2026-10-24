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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data item representing XP earned on a specific day for the chart.
 */
data class DailyXp(
    val dayLabel: String,
    val xp: Int,
    val goalMet: Boolean = false
)

/**
 * Custom bar chart drawn with Compose [Canvas] displaying recent daily XP history.
 */
@Composable
fun XpBarChart(
    history: List<DailyXp>,
    dailyGoal: Int = 50,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    val barColor = MaterialTheme.colorScheme.primary
    val goalMetColor = Color(0xFF2E7D32) // Forest green
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val goalLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "XP History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Goal: $dailyGoal XP/day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Bar Chart
            val maxY = maxOf(dailyGoal * 1.25f, (history.maxOfOrNull { it.xp } ?: dailyGoal).toFloat() * 1.15f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val bottomPadding = 4f
                val chartHeight = canvasHeight - bottomPadding

                // Draw baseline
                drawLine(
                    color = axisColor,
                    start = Offset(0f, chartHeight),
                    end = Offset(canvasWidth, chartHeight),
                    strokeWidth = 2f
                )

                // Draw daily goal dashed line
                val goalY = chartHeight - (dailyGoal / maxY * chartHeight)
                if (goalY in 0f..chartHeight) {
                    drawLine(
                        color = goalLineColor,
                        start = Offset(0f, goalY),
                        end = Offset(canvasWidth, goalY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // Draw bars
                val barCount = history.size
                val slotWidth = canvasWidth / barCount
                val barWidth = (slotWidth * 0.55f).coerceIn(12f, 40f)

                history.forEachIndexed { index, item ->
                    val barHeight = (item.xp / maxY * chartHeight).coerceIn(4f, chartHeight)
                    val x = index * slotWidth + (slotWidth - barWidth) / 2f
                    val y = chartHeight - barHeight

                    val color = if (item.xp >= dailyGoal) goalMetColor else barColor

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // X-axis day labels & values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                history.forEach { item ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${item.xp}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.xp >= dailyGoal) goalMetColor else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.dayLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun XpBarChartPreview() {
    MaterialTheme {
        XpBarChart(
            history = listOf(
                DailyXp("Mon", 35),
                DailyXp("Tue", 60, goalMet = true),
                DailyXp("Wed", 50, goalMet = true),
                DailyXp("Thu", 20),
                DailyXp("Fri", 75, goalMet = true),
                DailyXp("Sat", 40),
                DailyXp("Sun", 80, goalMet = true)
            ),
            dailyGoal = 50
        )
    }
}
