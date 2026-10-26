package dev.agam.learn.ui.lesson.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.domain.CodePuzzleBlankFeedback
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.runner.LanguageSupport
import dev.agam.learn.ui.components.CodeBlock
import dev.agam.learn.ui.theme.extendedColors

/**
 * Renders an offline code puzzle with a focused code context and four tap-to-select choices
 * for one blank at a time. Longer templates can still be expanded in full.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodePuzzleCard(
    puzzle: CodePuzzle,
    answers: Map<String, String>,
    onAnswerChange: (placeholder: String, option: String) -> Unit,
    isAnswerChecked: Boolean,
    feedback: List<CodePuzzleBlankFeedback> = emptyList(),
    modifier: Modifier = Modifier,
    languageSupport: LanguageSupport? = null,
    isInteractionLocked: Boolean = false
) {
    val extendedColors = MaterialTheme.extendedColors
    var showFullScaffold by remember(puzzle) { mutableStateOf(false) }
    var activeBlankIndex by remember(puzzle) { mutableStateOf(0) }
    val activeIndex = activeBlankIndex.coerceIn(0, puzzle.blanks.lastIndex.coerceAtLeast(0))
    val activeBlank = puzzle.blanks.getOrNull(activeIndex)
    val activeFeedback = feedback.firstOrNull { it.placeholder == activeBlank?.placeholder }
    val isLocked = isAnswerChecked || isInteractionLocked

    LaunchedEffect(puzzle) {
        activeBlankIndex = 0
    }
    LaunchedEffect(isAnswerChecked, feedback, puzzle) {
        if (isAnswerChecked) {
            val firstIncorrect = puzzle.blanks.indexOfFirst { blank ->
                feedback.firstOrNull { it.placeholder == blank.placeholder }?.isCorrect == false
            }
            if (firstIncorrect >= 0) {
                activeBlankIndex = firstIncorrect
                showFullScaffold = false
            }
        }
    }

    val lines = remember(puzzle.template) { puzzle.template.lines() }
    val isLongScaffold = lines.size > 20
    val contextTemplate = remember(puzzle.template, activeBlank?.placeholder, isLongScaffold, showFullScaffold) {
        if (isLongScaffold && !showFullScaffold && activeBlank != null) {
            extractBlankContext(puzzle.template, listOf(activeBlank.placeholder))
        } else {
            puzzle.template
        }
    }
    val displayedCode = remember(contextTemplate, answers, puzzle.blanks) {
        renderPuzzleCode(contextTemplate, puzzle.blanks.map { it.placeholder }, answers)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (!isLongScaffold || showFullScaffold) {
                            "Code Solution"
                        } else {
                            "Context for blank ${activeIndex + 1}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (isLongScaffold) {
                        OutlinedButton(
                            onClick = { showFullScaffold = !showFullScaffold },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (showFullScaffold) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showFullScaffold) "Focus current blank" else "Expand full code",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showFullScaffold) "Focus blank" else "Full scaffold (${lines.size} lines)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (languageSupport != null) {
                    CodeBlock(code = displayedCode, languageSupport = languageSupport)
                } else {
                    CodeBlock(code = displayedCode)
                }
            }
        }

        if (puzzle.blanks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                puzzle.blanks.forEachIndexed { index, blank ->
                    val blankFeedback = feedback.firstOrNull { it.placeholder == blank.placeholder }
                    val answered = !answers[blank.placeholder].isNullOrBlank()
                    val status = when {
                        blankFeedback?.isCorrect == true -> " ✓"
                        blankFeedback != null -> " ×"
                        answered -> " ✓"
                        else -> ""
                    }
                    OutlinedButton(
                        onClick = { activeBlankIndex = index },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = when {
                                    blankFeedback?.isCorrect == true -> "Blank ${index + 1}, correct"
                                    blankFeedback != null -> "Blank ${index + 1}, incorrect"
                                    answered -> "Blank ${index + 1}, answered"
                                    else -> "Blank ${index + 1}, unanswered"
                                }
                            },
                        border = BorderStroke(
                            if (index == activeIndex) 1.5.dp else 1.dp,
                            if (index == activeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text("Blank ${index + 1}$status")
                    }
                }
            }

            Text(
                text = "Blank ${activeIndex + 1} of ${puzzle.blanks.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val blank = activeBlank
            if (blank != null) {
                val currentValue = answers[blank.placeholder]
                val isBlankCorrect = activeFeedback?.isCorrect
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = blank.placeholder,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (isAnswerChecked && isBlankCorrect != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBlankCorrect) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = if (isBlankCorrect) "Correct" else "Incorrect",
                                        tint = if (isBlankCorrect) extendedColors.success else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (isBlankCorrect) "Correct" else "Incorrect",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isBlankCorrect) extendedColors.success else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup()
                        ) {
                            blank.options.forEach { option ->
                                val isSelected = currentValue == option
                                val isOptionAccepted = if (!isAnswerChecked) null else {
                                    blank.accepted.any { accepted ->
                                        if (blank.caseSensitive) option.trim() == accepted.trim()
                                        else option.trim().equals(accepted.trim(), ignoreCase = true)
                                    }
                                }
                                val containerColor = when {
                                    isAnswerChecked && isSelected && isBlankCorrect == true -> extendedColors.successContainer
                                    isAnswerChecked && isSelected && isBlankCorrect == false -> MaterialTheme.colorScheme.errorContainer
                                    !isAnswerChecked && isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                val contentColor = when {
                                    isAnswerChecked && isSelected && isBlankCorrect == true -> extendedColors.onSuccessContainer
                                    isAnswerChecked && isSelected && isBlankCorrect == false -> MaterialTheme.colorScheme.onErrorContainer
                                    !isAnswerChecked && isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                val border = when {
                                    isAnswerChecked && isOptionAccepted == true && !isSelected -> BorderStroke(1.5.dp, extendedColors.success)
                                    isSelected -> BorderStroke(
                                        1.5.dp,
                                        if (isAnswerChecked && isBlankCorrect == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .selectable(
                                            selected = isSelected,
                                            enabled = !isLocked,
                                            role = Role.RadioButton,
                                            onClick = { onAnswerChange(blank.placeholder, option) }
                                        ),
                                    shape = RoundedCornerShape(8.dp),
                                    color = containerColor,
                                    border = border
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = contentColor,
                                            softWrap = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isAnswerChecked && isSelected) {
                                            Icon(
                                                imageVector = if (isBlankCorrect == true) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = if (isBlankCorrect == true) "Correct" else "Incorrect",
                                                tint = if (isBlankCorrect == true) extendedColors.success else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isAnswerChecked && isBlankCorrect == false) {
                            val acceptedText = blank.accepted.joinToString(" or ") { "\"$it\"" }
                            Text(
                                text = "Accepted: $acceptedText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { activeBlankIndex = (activeIndex - 1).coerceAtLeast(0) },
                    enabled = activeIndex > 0,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("‹ Previous")
                }
                OutlinedButton(
                    onClick = { activeBlankIndex = (activeIndex + 1).coerceAtMost(puzzle.blanks.lastIndex) },
                    enabled = activeIndex < puzzle.blanks.lastIndex,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Next ›")
                }
            }
        }

        if (isAnswerChecked) {
            puzzle.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

/** Replaces placeholders in a puzzle template with selected answers or numbered hints. */
fun renderPuzzleCode(
    template: String,
    placeholders: List<String>,
    answers: Map<String, String>
): String {
    var code = template
    placeholders.forEachIndexed { index, placeholder ->
        val answer = answers[placeholder]
        val replacement = if (!answer.isNullOrBlank()) answer else "« Blank ${index + 1} »"
        code = code.replace(placeholder, replacement)
    }
    return code
}

/** Extracts the code around selected placeholders for phone-sized screens. */
fun extractBlankContext(
    code: String,
    placeholders: List<String>,
    contextLines: Int = 3
): String {
    val lines = code.lines()
    if (lines.size <= 20) return code

    val matchedLineIndices = mutableSetOf<Int>()
    lines.forEachIndexed { index, line ->
        if (placeholders.any { line.contains(it) }) {
            val start = (index - contextLines).coerceAtLeast(0)
            val end = (index + contextLines).coerceAtMost(lines.size - 1)
            for (i in start..end) matchedLineIndices.add(i)
        }
    }

    if (matchedLineIndices.isEmpty()) return code.take(1500)
    val sorted = matchedLineIndices.sorted()
    return buildString {
        var previous = -1
        sorted.forEach { index ->
            if (previous != -1 && index > previous + 1) append("// ...\n")
            append(lines[index]).append('\n')
            previous = index
        }
        if (previous < lines.lastIndex) append("// ...")
    }.trimEnd()
}
