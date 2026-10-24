package dev.agam.learn.ui.problem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Formats remaining seconds as MM:SS.
 */
fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

/**
 * UI component for the optional 45-minute mock interview timer.
 */
@Composable
fun ProblemTimer(
    secondsRemaining: Int,
    isRunning: Boolean,
    onToggleRunning: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timerColor = when {
        secondsRemaining == 0 -> MaterialTheme.colorScheme.error
        secondsRemaining < 300 -> Color(0xFFE65100) // Under 5 minutes warning
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⏱ ${formatTime(secondsRemaining)}",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = timerColor
        )

        Spacer(modifier = Modifier.width(8.dp))

        TextButton(
            onClick = onToggleRunning,
            colors = ButtonDefaults.textButtonColors(contentColor = timerColor)
        ) {
            Text(
                text = if (isRunning) "Pause" else "Start",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        TextButton(
            onClick = onReset,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            Text(
                text = "Reset",
                fontSize = 12.sp
            )
        }
    }
}

@Preview
@Composable
private fun ProblemTimerPreview() {
    MaterialTheme {
        ProblemTimer(
            secondsRemaining = 45 * 60,
            isRunning = false,
            onToggleRunning = {},
            onReset = {}
        )
    }
}
