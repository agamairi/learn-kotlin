package dev.agam.learn.ui.lesson.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agam.learn.ui.components.BrandAsset
import dev.agam.learn.ui.components.BrandArtwork
import dev.agam.learn.ui.experience.LocalReducedMotion
import dev.agam.learn.ui.theme.extendedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 5-correct-in-a-row (and 10, 15, ...) Koda flight celebration overlay.
 * Non-blocking, celebratory but brief (~1.4s), and provides a calm static badge under reduced motion.
 */
@Composable
fun LessonMilestoneOverlay(
    streakCount: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = LocalReducedMotion.current
) {
    val accessibilityManager = LocalAccessibilityManager.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    // Keep the ordinary celebration brief while allowing accessibility services to request
    // more time for its text and artwork.
    LaunchedEffect(streakCount, accessibilityManager) {
        val recommendedTimeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = MILESTONE_DURATION_MILLIS,
            containsIcons = true,
            containsText = true,
            containsControls = false
        ) ?: MILESTONE_DURATION_MILLIS
        delay(recommendedTimeout)
        currentOnDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$streakCount in a row milestone celebration"
            },
        contentAlignment = Alignment.Center
    ) {
        if (reducedMotion) {
            CalmStaticMilestoneBadge(
                streakCount = streakCount,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            AnimatedMilestoneFlightOverlay(
                streakCount = streakCount,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Non-motion calm static alternative for learners who prefer reduced motion.
 */
@Composable
private fun CalmStaticMilestoneBadge(
    streakCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandArtwork(
                asset = BrandAsset.MascotFly,
                modifier = Modifier.size(52.dp),
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "$streakCount in a row!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Consistent streak! Keep up the great work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Vibrant animated Koda flight & confetti celebration using original Koda artwork and teal-indigo palette.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AnimatedMilestoneFlightOverlay(
    streakCount: Int,
    modifier: Modifier = Modifier
) {
    val scaleAnim = remember { Animatable(0.6f) }
    val flyOffsetY = remember { Animatable(30f) }
    val confettiProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            flyOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(450, easing = FastOutSlowInEasing)
            )
        }
        launch {
            confettiProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            )
        }
    }

    val infiniteBob = rememberInfiniteTransition(label = "kodaFlightBob")
    val tiltAngle by infiniteBob.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flightTilt"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationY = flyOffsetY.value
            },
        contentAlignment = Alignment.Center
    ) {
        // Confetti burst particles in teal-indigo-amber family
        ConfettiBurst(progress = confettiProgress.value)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandArtwork(
                    asset = BrandAsset.MascotFly,
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer {
                            rotationZ = tiltAngle
                        },
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        maxItemsInEachRow = 2
                    ) {
                        Text(
                            text = "$streakCount in a row!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "✨",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = (MaterialTheme.typography.titleMedium.fontSize.value + 4).sp
                            )
                        )
                    }

                    Text(
                        text = "Koda is soaring with you! Keep going!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/**
 * Lightweight canvas confetti burst rendering particles from the active theme palette.
 */
@Composable
private fun ConfettiBurst(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val confettiColors = listOf(
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
        MaterialTheme.extendedColors.success
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val alpha = (1f - (progress * 0.9f)).coerceIn(0f, 1f)
        val maxRadius = size.width * 0.45f

        for (i in 0 until 16) {
            val angle = (i * (360f / 16f)) * (Math.PI / 180f).toFloat()
            val distance = progress * maxRadius * (0.6f + (i % 4) * 0.15f)
            val px = centerX + cos(angle.toDouble()).toFloat() * distance
            val py = centerY + sin(angle.toDouble()).toFloat() * distance
            val color = confettiColors[i % confettiColors.size].copy(alpha = alpha)

            rotate(degrees = progress * 180f + (i * 20f), pivot = Offset(px, py)) {
                if (i % 2 == 0) {
                    drawCircle(
                        color = color,
                        radius = 4.dp.toPx() * (1f - progress * 0.3f),
                        center = Offset(px, py)
                    )
                } else {
                    drawRect(
                        color = color,
                        topLeft = Offset(px - 4.dp.toPx(), py - 3.dp.toPx()),
                        size = Size(8.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }
    }
}

private const val MILESTONE_DURATION_MILLIS = 1_400L
