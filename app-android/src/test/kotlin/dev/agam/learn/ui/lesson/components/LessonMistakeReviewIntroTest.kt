package dev.agam.learn.ui.lesson.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
class LessonMistakeReviewIntroTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun introExplainsPendingCountAndWaitsForAnExplicitAccessibleAction() {
        var startCalls = 0
        compose.mainClock.autoAdvance = false

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    LessonMistakeReviewIntro(
                        pendingCount = 1,
                        onStartReview = { startCalls++ }
                    )
                }
            }
        }

        compose.onNodeWithText("Let’s review your mistakes").assertIsDisplayed()
        compose.onNodeWithText("1 question to revisit").assertIsDisplayed()
        compose.onNodeWithText("Review mistakes")
            .assertIsDisplayed()
            .assertHasClickAction()

        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("The learner must choose when to begin", 0, startCalls)

        compose.onNodeWithText("Review mistakes").performClick()
        assertEquals("The action is delivered once", 1, startCalls)
    }

    @Test
    fun introAndActionFitNarrowWidthWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 1.5f),
                LocalReducedMotion provides true
            ) {
                LearnKotlinTheme(darkTheme = false) {
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        LessonMistakeReviewIntro(
                            pendingCount = 12,
                            onStartReview = {},
                            reducedMotion = true
                        )
                    }
                }
            }
        }

        val maxRight = with(compose.density) { 320.dp.toPx() }
        val titleBounds = compose.onNodeWithText("Let’s review your mistakes")
            .fetchSemanticsNode().boundsInRoot
        val countBounds = compose.onNodeWithText("12 questions to revisit")
            .fetchSemanticsNode().boundsInRoot
        val buttonBounds = compose.onNodeWithText("Review mistakes")
            .fetchSemanticsNode().boundsInRoot

        assertTrue("title stays within the 320dp parent", titleBounds.right <= maxRight + 1f)
        assertTrue("count stays within the 320dp parent", countBounds.right <= maxRight + 1f)
        assertTrue("action stays within the 320dp parent", buttonBounds.right <= maxRight + 1f)
    }
}
