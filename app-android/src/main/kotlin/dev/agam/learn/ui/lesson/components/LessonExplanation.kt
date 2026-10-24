package dev.agam.learn.ui.lesson.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agam.learn.ui.components.CodeBlock

/**
 * Introductory lesson view presenting the conceptual explanation before starting exercises.
 */
@Composable
fun LessonExplanation(
    lessonTitle: String,
    chapterTitle: String,
    explanation: String,
    onStartPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = chapterTitle.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Text(
                text = lessonTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Render explanation paragraphs and detect markdown code blocks if any
            RenderExplanationText(explanation)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartPractice,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "Start Exercises",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RenderExplanationText(explanation: String) {
    val parts = explanation.split("```")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            // Code block inside fenced triple backticks
            val lines = part.trim().lines()
            val code = if (lines.firstOrNull()?.matches(Regex("^[a-zA-Z]+$")) == true) {
                lines.drop(1).joinToString("\n")
            } else {
                part.trim()
            }
            CodeBlock(code = code)
        } else {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                Text(
                    text = trimmed,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
