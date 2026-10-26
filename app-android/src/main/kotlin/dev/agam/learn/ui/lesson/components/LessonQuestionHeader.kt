package dev.agam.learn.ui.lesson.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.model.Exercise
import dev.agam.learn.ui.components.BrandArtwork
import dev.agam.learn.ui.components.BrandAsset

/**
 * A quiet exercise guide with Koda alongside the instruction.
 * Does not duplicate the exercise prompt text already displayed inside the cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonQuestionHeader(
    exercise: Exercise,
    consecutiveStreak: Int,
    modifier: Modifier = Modifier
) {
    val (exerciseTypeTitle, exerciseGuide) = when (exercise) {
        is Exercise.MultipleChoice -> "Multiple Choice" to "Choose the single best answer below"
        is Exercise.Cloze -> "Fill in the Blanks" to "Complete the code by filling in the blanks"
        is Exercise.PredictOutput -> "Predict Output" to "Trace this program and predict stdout"
        is Exercise.WriteFunction -> "Code puzzle" to "Build the code by choosing code blanks"
        is Exercise.MultiPartProblem -> "Multipart puzzle" to "Build the code one stage at a time"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                val streakDescription = if (consecutiveStreak > 1) {
                    " $consecutiveStreak correct in a row."
                } else {
                    ""
                }
                contentDescription = "Koda guide: $exerciseTypeTitle. $exerciseGuide.$streakDescription"
            },
        verticalAlignment = Alignment.Top
    ) {
        BrandArtwork(
            asset = BrandAsset.MascotThink,
            modifier = Modifier.size(48.dp),
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                maxItemsInEachRow = 2
            ) {
                Text(
                    text = exerciseTypeTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    softWrap = true
                )

                if (consecutiveStreak > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BrandArtwork(
                            asset = BrandAsset.StreakFlame,
                            modifier = Modifier.size(22.dp),
                            contentDescription = null
                        )
                        Text(
                            text = "$consecutiveStreak in a row",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = exerciseGuide,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true
            )
        }
    }
}
