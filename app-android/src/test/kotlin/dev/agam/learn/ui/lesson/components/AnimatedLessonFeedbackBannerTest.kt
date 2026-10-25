package dev.agam.learn.ui.lesson.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.agam.learn.ui.theme.LearnKotlinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AnimatedLessonFeedbackBannerTest {
    @get:Rule
    val compose = createComposeRule()

    private var visible by mutableStateOf(true)
    private var feedbackKey by mutableStateOf("question-1")
    private var correct by mutableStateOf<Boolean?>(true)
    private var explanation by mutableStateOf("First answer explanation")
    private var interviewMode by mutableStateOf(false)
    private var continues = 0

    private fun showBanner() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    AnimatedLessonFeedbackBanner(
                        visible = visible,
                        feedbackKey = feedbackKey,
                        isCorrect = correct == true,
                        explanation = explanation,
                        isAiAvailable = true,
                        isInterviewMode = interviewMode,
                        hasApiKey = true,
                        onContinue = {
                            continues++
                            feedbackKey = "question-${continues + 1}"
                            visible = false
                            correct = null
                            explanation = ""
                        },
                        onMoreLikeThis = {}
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun correctFeedbackAndExplanationSurviveEveryExitFrame() {
        showBanner()
        compose.onNodeWithText("Excellent!").assertExists()
        compose.onNodeWithText("Continue").performClick()

        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Excellent!").assertExists()
            compose.onNodeWithText("First answer explanation").assertExists()
            compose.onNodeWithText("Not quite").assertDoesNotExist()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Excellent!").assertDoesNotExist()
        compose.onNodeWithText("First answer explanation").assertDoesNotExist()
        assertEquals(1, continues)
    }

    @Test
    fun nextAnswerUsesItsOwnResultEvenWhenPreviousExitIsStillRunning() {
        showBanner()
        compose.onNodeWithText("Continue").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            correct = false
            explanation = "Second answer explanation"
            visible = true
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Not quite").assertExists()
        compose.onNodeWithText("Second answer explanation").assertExists()
        compose.onNodeWithText("Excellent!").assertDoesNotExist()
        compose.onNodeWithText("First answer explanation").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()
        assertEquals(2, continues)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Not quite").assertExists()
        compose.onNodeWithText("Second answer explanation").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Not quite").assertDoesNotExist()
        compose.runOnIdle {
            correct = true
            explanation = "Third answer explanation"
            visible = true
        }
        // Android measure/layout must run before advancing a newly re-entering animation.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Excellent!").assertExists()
        compose.onNodeWithText("Third answer explanation").assertExists()
        compose.onNodeWithText("Not quite").assertDoesNotExist()
    }

    @Test
    fun exitingFeedbackCannotContinueAgainAndUsesCurrentInterviewAccess() {
        showBanner()
        compose.onNodeWithText("✨ More like this").assertExists()
        val staleContinue = compose.onNodeWithText("Continue")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { staleContinue() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.runOnIdle { interviewMode = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("✨ More like this").assertDoesNotExist()
        compose.onNodeWithText("Excellent!").assertExists()
        assertEquals(1, continues)
    }
}
