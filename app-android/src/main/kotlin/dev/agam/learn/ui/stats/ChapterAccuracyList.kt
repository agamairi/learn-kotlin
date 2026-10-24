package dev.agam.learn.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Metric tracking user exercise accuracy within a specific chapter.
 */
data class ChapterAccuracy(
    val chapterId: String,
    val chapterTitle: String,
    val attempted: Int,
    val correct: Int,
    val accuracyPercent: Int
)

/**
 * Renders per-chapter exercise accuracy with visual progress indicators.
 */
@Composable
fun ChapterAccuracyList(
    accuracies: List<ChapterAccuracy>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Per-Chapter Accuracy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (accuracies.isEmpty()) {
                Text(
                    text = "No chapter exercises attempted yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                accuracies.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.chapterTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (item.attempted == 0) {
                                    "No attempts yet"
                                } else {
                                    "${item.accuracyPercent}% (${item.correct}/${item.attempted})"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    item.attempted == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                    item.accuracyPercent >= 80 -> Color(0xFF2E7D32)
                                    item.accuracyPercent >= 60 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val progress = if (item.attempted > 0) item.correct.toFloat() / item.attempted else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = when {
                                item.accuracyPercent >= 80 -> Color(0xFF2E7D32)
                                item.accuracyPercent >= 60 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChapterAccuracyListPreview() {
    MaterialTheme {
        ChapterAccuracyList(
            accuracies = listOf(
                ChapterAccuracy("ch01", "Syntax Basics", 12, 11, 92),
                ChapterAccuracy("ch02", "Strings & Templates", 15, 13, 87),
                ChapterAccuracy("ch03", "Collections & Transformations", 20, 16, 80),
                ChapterAccuracy("ch04", "Testing & Assertions", 8, 6, 75),
                ChapterAccuracy("ch05", "Data Modeling", 10, 5, 50)
            )
        )
    }
}
