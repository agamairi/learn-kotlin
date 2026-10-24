package dev.agam.learn.ui.lesson.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.model.Exercise
import dev.agam.learn.runner.LanguageSupport
import dev.agam.learn.ui.components.CodeBlock
import dev.agam.learn.ui.theme.extendedColors

/**
 * Predict output exercise card displaying a program and a text field for predicted stdout.
 */
@Composable
fun PredictOutputCard(
    exercise: Exercise.PredictOutput,
    userOutput: String,
    onOutputChange: (String) -> Unit,
    isAnswerChecked: Boolean,
    isCorrect: Boolean?,
    modifier: Modifier = Modifier,
    languageSupport: LanguageSupport? = null
) {
    val extendedColors = MaterialTheme.extendedColors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = exercise.prompt,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (languageSupport != null) {
            CodeBlock(code = exercise.code, languageSupport = languageSupport)
        } else {
            CodeBlock(code = exercise.code)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "What is the expected stdout output?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val borderColor = when {
            !isAnswerChecked -> MaterialTheme.colorScheme.outline
            isCorrect == true -> extendedColors.success
            else -> MaterialTheme.colorScheme.error
        }

        OutlinedTextField(
            value = userOutput,
            onValueChange = onOutputChange,
            enabled = !isAnswerChecked,
            placeholder = {
                Text(
                    text = "Type predicted output here...",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            minLines = 2,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isAnswerChecked) borderColor else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = borderColor,
                disabledBorderColor = borderColor,
                disabledTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (isAnswerChecked && isCorrect != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCorrect) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Correct",
                        tint = extendedColors.success,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Correct output",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = extendedColors.success
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Incorrect",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Incorrect output",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (isAnswerChecked && isCorrect == false) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Expected output:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                CodeBlock(code = exercise.expectedOutput)
            }
        }
    }
}
