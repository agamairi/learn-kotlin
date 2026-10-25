package dev.agam.learn.ui.lesson.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.agam.learn.ui.experience.LocalReducedMotion

/**
 * Shows answer feedback with a slide transition while retaining the outgoing result until it exits.
 *
 * The result is cached whenever it is visible. If the lesson advances and clears its live answer
 * state, the exiting banner keeps showing that cached result. A newly checked answer uses the live
 * values immediately, including when it interrupts an in-flight exit animation.
 */
@Composable
internal fun AnimatedLessonFeedbackBanner(
    visible: Boolean,
    feedbackKey: String,
    isCorrect: Boolean,
    explanation: String,
    onContinue: () -> Unit,
    isAiAvailable: Boolean = false,
    isInterviewMode: Boolean = false,
    hasApiKey: Boolean = false,
    onMoreLikeThis: (() -> Unit)? = null
) {
    var cachedResult by remember { mutableStateOf<FeedbackResult?>(null) }
    var wasVisible by remember { mutableStateOf(false) }
    var cachedFeedbackKey by remember { mutableStateOf<String?>(null) }
    var continueConsumed by remember { mutableStateOf(false) }

    SideEffect {
        if (visible) {
            cachedResult = FeedbackResult(isCorrect, explanation)
            if (!wasVisible || cachedFeedbackKey != feedbackKey) {
                continueConsumed = false
            }
            cachedFeedbackKey = feedbackKey
        }
        wasVisible = visible
    }

    val resultToShow = if (visible) FeedbackResult(isCorrect, explanation) else cachedResult
    val reducedMotion = LocalReducedMotion.current

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn() else slideInVertically(initialOffsetY = { it }),
        exit = if (reducedMotion) fadeOut() else slideOutVertically(targetOffsetY = { it })
    ) {
        resultToShow?.let { result ->
            FeedbackBanner(
                isCorrect = result.isCorrect,
                explanation = result.explanation,
                onContinue = {
                    if (visible && !continueConsumed) {
                        continueConsumed = true
                        onContinue()
                    }
                },
                continueEnabled = visible && !continueConsumed,
                isAiAvailable = isAiAvailable,
                isInterviewMode = isInterviewMode,
                hasApiKey = hasApiKey,
                onMoreLikeThis = onMoreLikeThis
            )
        }
    }
}

private data class FeedbackResult(
    val isCorrect: Boolean,
    val explanation: String
)
