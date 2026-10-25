package dev.agam.learn.ui.lesson.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * Question header with Koda in a thinking pose and a speech-bubble compact exercise guide.
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
        is Exercise.WriteFunction -> "Code Studio Drill" to "Write and verify your function"
        is Exercise.MultiPartProblem -> "Stripe Problem Studio" to "Solve the multi-step problem"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Koda guide: $exerciseTypeTitle. $exerciseGuide."
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandArtwork(
            asset = BrandAsset.MascotThink,
            modifier = Modifier.size(54.dp),
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Speech-bubble styled container with top-start tail pointing to Koda
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 2
                ) {
                    Text(
                        text = exerciseTypeTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        softWrap = true
                    )

                    if (consecutiveStreak > 1) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                BrandArtwork(
                                    asset = BrandAsset.StreakFlame,
                                    modifier = Modifier.size(12.dp),
                                    contentDescription = null
                                )
                                Text(
                                    text = "$consecutiveStreak in a row",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
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
}
