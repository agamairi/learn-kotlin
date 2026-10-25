package dev.agam.learn.ui.lesson.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.agam.learn.model.Exercise
import dev.agam.learn.ui.experience.LocalReducedMotion
import dev.agam.learn.ui.theme.LearnKotlinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LessonMilestoneTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun milestoneOverlayUnderReducedMotionDisplaysCalmStaticBadgeAndAutoDismisses() {
        var dismissed = false
        compose.mainClock.autoAdvance = false

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LessonMilestoneOverlay(
                            streakCount = 5,
                            onDismiss = { dismissed = true },
                            reducedMotion = true
                        )
                    }
                }
            }
        }

        // Under reduced motion, calm static badge is rendered immediately
        compose.onNodeWithText("5 in a row!").assertExists()
        compose.onNodeWithText("Consistent streak! Keep up the great work.").assertExists()

        // Advance before 1.4s: still visible
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("Should not dismiss before 1.4s duration", false, dismissed)

        // Advance past 1.4s auto-dismiss threshold
        compose.mainClock.advanceTimeBy(500)
        assertTrue("Milestone should auto-dismiss after ~1.4s", dismissed)
    }

    @Test
    fun milestoneUsesAccessibilityRecommendedTimeout() {
        var requestedTimeout = 0L
        var dismissed = false
        compose.mainClock.autoAdvance = false

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalAccessibilityManager provides object : AccessibilityManager {
                        override fun calculateRecommendedTimeoutMillis(
                            originalTimeoutMillis: Long,
                            containsIcons: Boolean,
                            containsText: Boolean,
                            containsControls: Boolean
                        ): Long {
                            requestedTimeout = originalTimeoutMillis
                            assertTrue(containsIcons)
                            assertTrue(containsText)
                            assertEquals(false, containsControls)
                            return 3_000L
                        }
                    }
                ) {
                    LessonMilestoneOverlay(
                        streakCount = 5,
                        onDismiss = { dismissed = true },
                        reducedMotion = true
                    )
                }
            }
        }

        compose.waitForIdle()
        assertEquals(1_400L, requestedTimeout)
        compose.mainClock.advanceTimeBy(2_500)
        assertEquals(false, dismissed)
        compose.mainClock.advanceTimeBy(600)
        assertTrue(dismissed)
    }

    @Test
    fun questionHeaderDisplaysKodaGuideWithoutDuplicatingExerciseCardPrompt() {
        val exercise = Exercise.MultipleChoice(
            id = "mc-test-1",
            prompt = "What is the capital of Kotlin?",
            options = listOf("K", "JetBrains", "Island"),
            correctIndex = 2,
            explanation = "Named after Kotlin Island."
        )

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                LessonQuestionHeader(
                    exercise = exercise,
                    consecutiveStreak = 3
                )
            }
        }

        // Question header shows exercise category guide
        compose.onNodeWithText("Multiple Choice").assertExists()
        compose.onNodeWithText("Choose the single best answer below").assertExists()
        compose.onNodeWithText("3 in a row").assertExists()

        // Card prompt text is not duplicated in the question header
        compose.onNodeWithText("What is the capital of Kotlin?").assertDoesNotExist()
    }

    @Test
    fun questionHeaderFitsNarrowWidthAtLargeFontScale() {
        val exercise = Exercise.MultipleChoice(
            id = "mc-narrow",
            prompt = "A prompt that belongs to the exercise card",
            options = listOf("one", "two"),
            correctIndex = 0,
            explanation = "Because one is first"
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LearnKotlinTheme(darkTheme = false) {
                    Box(Modifier.width(320.dp)) {
                        LessonQuestionHeader(exercise = exercise, consecutiveStreak = 12)
                    }
                }
            }
        }

        val headerBounds = compose.onNodeWithContentDescription(
            "Koda guide: Multiple Choice. Choose the single best answer below. 12 correct in a row.",
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val titleBounds = compose.onNodeWithText("Multiple Choice", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val streakBounds = compose.onNodeWithText("12 in a row", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val maxRight = with(compose.density) { 320.dp.toPx() }

        assertTrue("header must remain inside its 320dp parent", headerBounds.right <= maxRight + 1f)
        assertTrue("exercise type must remain inside its parent", titleBounds.right <= maxRight + 1f)
        assertTrue("streak label must remain inside its parent", streakBounds.right <= maxRight + 1f)
        compose.onNodeWithText("Choose the single best answer below").assertExists()
    }

    @Test
    fun milestoneHeadlineAndEmojiStayWithinNarrowWidthAtLargeFontScale() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LearnKotlinTheme(darkTheme = false) {
                    Box(Modifier.width(320.dp)) {
                        LessonMilestoneOverlay(
                            streakCount = 15,
                            onDismiss = {},
                            reducedMotion = false
                        )
                    }
                }
            }
        }

        val headline = compose.onNodeWithText("15 in a row!", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val sparkle = compose.onNodeWithText("✨", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val parentRight = with(compose.density) { 320.dp.toPx() }
        assertTrue("milestone headline must stay within its parent", headline.right <= parentRight + 1f)
        assertTrue("milestone sparkle must stay within its parent", sparkle.right <= parentRight + 1f)
    }
}
