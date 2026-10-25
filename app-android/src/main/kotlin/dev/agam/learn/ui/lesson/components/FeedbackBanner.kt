package dev.agam.learn.ui.lesson.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.ai.AI_API_KEY_MISSING_MESSAGE
import dev.agam.learn.ui.theme.extendedColors

/**
 * Bottom feedback banner displayed after checking an answer.
 */
@Composable
fun FeedbackBanner(
    isCorrect: Boolean,
    explanation: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    isAiAvailable: Boolean = false,
    isInterviewMode: Boolean = false,
    hasApiKey: Boolean = false,
    onMoreLikeThis: (() -> Unit)? = null,
    continueEnabled: Boolean = true
) {
    val extendedColors = MaterialTheme.extendedColors
    val containerColor = if (isCorrect) extendedColors.successContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isCorrect) extendedColors.success else MaterialTheme.colorScheme.error
    val buttonColor = if (isCorrect) extendedColors.success else MaterialTheme.colorScheme.error

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = if (isCorrect) "Correct" else "Needs Improvement",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isCorrect) "Excellent!" else "Not quite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            if (explanation.isNotBlank()) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isAiAvailable && !isInterviewMode) {
                if (hasApiKey) {
                    OutlinedButton(
                        onClick = { onMoreLikeThis?.invoke() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "✨ More like this",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = AI_API_KEY_MISSING_MESSAGE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onContinue,
                enabled = continueEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White,
                    disabledContainerColor = buttonColor,
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
