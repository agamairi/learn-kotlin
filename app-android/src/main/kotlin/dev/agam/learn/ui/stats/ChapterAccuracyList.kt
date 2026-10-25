package dev.agam.learn.ui.stats

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agam.learn.ui.theme.extendedColors

/** Accuracy from the learner's recorded exercise attempts in a chapter. */
data class ChapterAccuracy(
    val chapterId: String,
    val chapterTitle: String,
    val attempted: Int,
    val correct: Int,
    val accuracyPercent: Int
)

/** Renders accuracy and attempt counts from the learner's existing chapter data. */
@Composable
fun ChapterAccuracyList(
    accuracies: List<ChapterAccuracy>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
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
            Text(
                text = "Chapter accuracy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Accuracy across recorded exercises",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))
            if (accuracies.isEmpty()) {
                Text(
                    text = "Chapter accuracy isn't available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                accuracies.forEachIndexed { index, item ->
                    if (index > 0) {
                        Spacer(Modifier.height(12.dp))
                    }
                    ChapterAccuracyRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun ChapterAccuracyRow(item: ChapterAccuracy) {
    val attempted = item.attempted.coerceAtLeast(0)
    val correct = item.correct.coerceIn(0, attempted)
    val hasAttempts = attempted > 0
    val percent = if (hasAttempts) item.accuracyPercent.coerceIn(0, 100) else 0
    val accuracyColor = when {
        !hasAttempts -> MaterialTheme.colorScheme.onSurfaceVariant
        percent >= 80 -> MaterialTheme.extendedColors.success
        percent >= 60 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val summary = if (hasAttempts) {
        "$correct correct of $attempted exercises, $percent percent accuracy"
    } else {
        "No attempts yet"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.chapterTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "${item.chapterTitle}: $summary"
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasAttempts) "$correct of $attempted correct" else "No attempts yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (hasAttempts) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = accuracyColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (hasAttempts) {
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { (correct.toFloat() / attempted).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .semantics { contentDescription = "${item.chapterTitle}: $summary" },
                color = accuracyColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    }
}
