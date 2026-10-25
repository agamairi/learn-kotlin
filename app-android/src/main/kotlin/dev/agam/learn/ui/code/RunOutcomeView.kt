package dev.agam.learn.ui.code

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.agam.learn.runner.Diagnostic
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.TestFailure
import dev.agam.learn.ui.theme.extendedColors

/**
 * Renders the outcome of a code execution or test run.
 */
@Composable
fun RunOutcomeView(
    outcome: RunOutcome?,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    if (outcome == null) return

    val colorScheme = MaterialTheme.colorScheme
    val extendedColors = MaterialTheme.extendedColors
    val (containerColor, contentColor, borderColor) = when (outcome) {
        is RunOutcome.Passed -> Triple(
            extendedColors.successContainer,
            extendedColors.onSuccessContainer,
            extendedColors.success
        )
        is RunOutcome.TestsFailed, is RunOutcome.CompileError -> Triple(
            colorScheme.errorContainer,
            colorScheme.onErrorContainer,
            colorScheme.error
        )
        is RunOutcome.Timeout -> Triple(
            colorScheme.tertiaryContainer,
            colorScheme.onTertiaryContainer,
            colorScheme.tertiary
        )
        is RunOutcome.Unavailable -> Triple(
            colorScheme.surfaceVariant,
            colorScheme.onSurfaceVariant,
            colorScheme.outline
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (outcome) {
                        is RunOutcome.Passed -> "✓ Tests Passed (${outcome.testsRun} run)"
                        is RunOutcome.TestsFailed -> "✗ Tests Failed (${outcome.failures.size} failure(s))"
                        is RunOutcome.CompileError -> "✗ Compilation Error"
                        is RunOutcome.Timeout -> "⏱ Execution Timed Out"
                        is RunOutcome.Unavailable -> "⚠ Compiler Unavailable"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss outcome",
                            tint = contentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (outcome) {
                is RunOutcome.Passed -> {
                    if (outcome.stdout.isNotBlank()) {
                        Text(
                            text = "Standard Output:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                        TerminalOutputBox(output = outcome.stdout)
                    }
                }
                is RunOutcome.TestsFailed -> {
                    outcome.failures.forEachIndexed { index, failure ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "• ${failure.testName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                                softWrap = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (failure.message.isNotBlank()) {
                                Text(
                                    text = failure.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = contentColor.copy(alpha = 0.9f),
                                    softWrap = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 2.dp)
                                )
                            }
                        }
                    }

                    if (outcome.stdout.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Standard Output:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                        TerminalOutputBox(output = outcome.stdout)
                    }
                }
                is RunOutcome.CompileError -> {
                    if (outcome.diagnostics.isEmpty()) {
                        Text(
                            text = "Compilation failed with unspecified errors.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor
                        )
                    } else {
                        outcome.diagnostics.forEach { diag ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = "${diag.line}:${diag.column}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = contentColor,
                                    modifier = Modifier.width(50.dp)
                                )
                                Text(
                                    text = "[${diag.severity.uppercase()}] ${diag.message}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = contentColor,
                                    modifier = Modifier.weight(1f),
                                    softWrap = true
                                )
                            }
                        }
                    }
                }
                is RunOutcome.Timeout -> {
                    Text(
                        text = "Execution exceeded the ${outcome.seconds}-second timeout limit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
                is RunOutcome.Unavailable -> {
                    Text(
                        text = outcome.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalOutputBox(output: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(Color(0xFF212121), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(
            text = output,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFFE0E0E0),
            softWrap = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun RunOutcomePassedPreview() {
    MaterialTheme {
        RunOutcomeView(
            outcome = RunOutcome.Passed(
                testsRun = 4,
                stdout = "Processing 10 records...\nDone."
            )
        )
    }
}

@Preview
@Composable
private fun RunOutcomeFailedPreview() {
    MaterialTheme {
        RunOutcomeView(
            outcome = RunOutcome.TestsFailed(
                failures = listOf(
                    TestFailure("testPositiveNumbers", "expected:<[2, 4]> but was:<[1, 2, 3, 4]>")
                ),
                stdout = ""
            )
        )
    }
}

@Preview
@Composable
private fun RunOutcomeCompileErrorPreview() {
    MaterialTheme {
        RunOutcomeView(
            outcome = RunOutcome.CompileError(
                diagnostics = listOf(
                    Diagnostic(line = 3, column = 14, severity = "ERROR", message = "Unresolved reference: foo"),
                    Diagnostic(line = 5, column = 5, severity = "WARNING", message = "Variable 'x' is never used")
                )
            )
        )
    }
}
