package dev.agam.learn.ui.lesson.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
 * Cloze exercise card displaying a code template with interactive blank input fields.
 */
@Composable
fun ClozeCard(
    exercise: Exercise.Cloze,
    answers: Map<String, String>,
    onAnswerChange: (placeholder: String, value: String) -> Unit,
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

        // Monospace template container
        if (languageSupport != null) {
            CodeBlock(code = exercise.template, languageSupport = languageSupport)
        } else {
            CodeBlock(code = exercise.template)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Fill in the blank${if (exercise.blanks.size > 1) "s" else ""}:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        exercise.blanks.forEachIndexed { index, blank ->
            val currentVal = answers[blank.placeholder] ?: ""
            val isBlankCorrect = if (!isAnswerChecked) null else {
                if (blank.caseSensitive) {
                    blank.accepted.any { it.trim() == currentVal.trim() }
                } else {
                    blank.accepted.any { it.trim().equals(currentVal.trim(), ignoreCase = true) }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Blank ${index + 1} (${blank.placeholder})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isAnswerChecked && isBlankCorrect != null) {
                            if (isBlankCorrect) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Correct",
                                        tint = extendedColors.success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Correct",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extendedColors.success,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Incorrect",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Incorrect",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    val borderColor = when {
                        !isAnswerChecked -> MaterialTheme.colorScheme.outline
                        isBlankCorrect == true -> extendedColors.success
                        else -> MaterialTheme.colorScheme.error
                    }

                    OutlinedTextField(
                        value = currentVal,
                        onValueChange = { onAnswerChange(blank.placeholder, it) },
                        enabled = !isAnswerChecked,
                        placeholder = {
                            Text(
                                text = "Enter code fragment...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isAnswerChecked) borderColor else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = borderColor,
                            disabledBorderColor = borderColor,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isAnswerChecked && isBlankCorrect == false) {
                        val acceptedList = blank.accepted.joinToString(" or ") { "\"$it\"" }
                        Text(
                            text = "Accepted: $acceptedList",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
