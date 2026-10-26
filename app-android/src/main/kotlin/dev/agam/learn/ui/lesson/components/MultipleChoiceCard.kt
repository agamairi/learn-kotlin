package dev.agam.learn.ui.lesson.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.agam.learn.model.Exercise
import dev.agam.learn.runner.LanguageSupport
import dev.agam.learn.ui.components.CodeBlock
import dev.agam.learn.ui.experience.LocalReducedMotion
import dev.agam.learn.ui.theme.extendedColors

/**
 * Multiple choice exercise card with selectable options and instant feedback highlighting.
 */
@Composable
fun MultipleChoiceCard(
    exercise: Exercise.MultipleChoice,
    selectedOptionIndex: Int?,
    onOptionSelected: (Int) -> Unit,
    isAnswerChecked: Boolean,
    isCorrect: Boolean?,
    modifier: Modifier = Modifier,
    languageSupport: LanguageSupport? = null
) {
    val extendedColors = MaterialTheme.extendedColors
    val reducedMotion = LocalReducedMotion.current
    val optionShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = exercise.prompt,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!exercise.code.isNullOrBlank()) {
            if (languageSupport != null) {
                CodeBlock(code = exercise.code!!, languageSupport = languageSupport)
            } else {
                CodeBlock(code = exercise.code!!)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        exercise.options.forEachIndexed { index, optionText ->
            val isSelected = selectedOptionIndex == index
            val isOptionCorrect = index == exercise.correctIndex

            val targetContainerColor = when {
                !isAnswerChecked -> if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
                isOptionCorrect -> extendedColors.successContainer.copy(alpha = 0.56f)
                isSelected && !isOptionCorrect -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.56f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            }

            val targetBorderColor = when {
                !isAnswerChecked -> if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                isOptionCorrect -> extendedColors.success
                isSelected && !isOptionCorrect -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }

            val optionTextColor = when {
                !isAnswerChecked -> MaterialTheme.colorScheme.onSurface
                isOptionCorrect -> extendedColors.onSuccessContainer
                isSelected && !isOptionCorrect -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            val containerColor = if (reducedMotion) {
                targetContainerColor
            } else {
                animateColorAsState(targetContainerColor, label = "optionContainerColor").value
            }

            val borderColor = if (reducedMotion) {
                targetBorderColor
            } else {
                animateColorAsState(targetBorderColor, label = "optionBorderColor").value
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(optionShape)
                    .clickable(enabled = !isAnswerChecked) {
                        onOptionSelected(index)
                    }
                    .semantics {
                        role = Role.RadioButton
                        selected = isSelected
                    },
                shape = optionShape,
                color = containerColor,
                border = BorderStroke(
                    width = if (isSelected || (isAnswerChecked && isOptionCorrect)) 1.5.dp else 1.dp,
                    color = borderColor
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val letter = ('A'.code + index).toChar().toString()
                    val markerColor = when {
                        !isAnswerChecked && isSelected -> MaterialTheme.colorScheme.primary
                        isOptionCorrect -> extendedColors.success
                        isSelected && !isOptionCorrect -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .heightIn(min = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAnswerChecked && isOptionCorrect) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Correct",
                                tint = markerColor,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (isAnswerChecked && isSelected && !isOptionCorrect) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Incorrect",
                                tint = markerColor,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = markerColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = optionText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = optionTextColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
