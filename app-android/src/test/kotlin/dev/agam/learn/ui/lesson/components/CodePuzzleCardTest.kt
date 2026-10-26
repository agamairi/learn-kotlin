package dev.agam.learn.ui.lesson.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.agam.learn.model.Blank
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.domain.CodePuzzleBlankFeedback
import dev.agam.learn.ui.theme.LearnKotlinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CodePuzzleCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val radioButtonMatcher = SemanticsMatcher.expectValue(
        SemanticsProperties.Role,
        Role.RadioButton
    )

    @Test
    fun longScaffoldShowsOneBlankAtATimeAndKeepsLateSelectionVisibleAndLockedAfterCheck() {
        val template = (1..24).joinToString("\n") { line ->
            when (line) {
                4 -> "val count = ____1"
                23 -> "fun total(): Int = ____2"
                else -> "// scaffold line $line"
            }
        }
        val puzzle = CodePuzzle(
            template = template,
            blanks = listOf(
                Blank("____1", listOf("10"), options = listOf("10", "20", "30", "40")),
                Blank("____2", listOf("42"), options = listOf("42", "0", "1", "2"))
            ),
            explanation = "The total function returns the selected count."
        )
        var answers by mutableStateOf<Map<String, String>>(emptyMap())
        var checked by mutableStateOf(false)
        var feedback by mutableStateOf(emptyList<CodePuzzleBlankFeedback>())

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    CodePuzzleCard(
                        puzzle = puzzle,
                        answers = answers,
                        onAnswerChange = { placeholder, option -> answers = answers + (placeholder to option) },
                        isAnswerChecked = checked,
                        feedback = feedback
                    )
                }
            }
        }

        compose.onNodeWithText("Full scaffold (24 lines)").assertExists()
        compose.onNodeWithText("Blank 1 of 2").assertExists()
        val choices = compose.onAllNodes(radioButtonMatcher, useUnmergedTree = true)
        choices.assertCountEquals(4)
        compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).assertCountEquals(0)

        // Only the active blank's four options are composed. Navigate directly to the late blank.
        compose.onNodeWithContentDescription("Blank 2, unanswered").performScrollTo().performClick()
        compose.onNodeWithText("Blank 2 of 2").assertExists()
        compose.onAllNodes(radioButtonMatcher, useUnmergedTree = true).assertCountEquals(4)
        compose.onAllNodes(radioButtonMatcher, useUnmergedTree = true)[0].performScrollTo().performClick()
        compose.onAllNodes(radioButtonMatcher, useUnmergedTree = true)[0].assertIsSelected()
        compose.onNodeWithText("fun total(): Int = 42", substring = true, useUnmergedTree = true).assertExists()

        // Full scaffold is optional and still renders selected answers from the original template.
        compose.onNodeWithText("Full scaffold (24 lines)").performScrollTo().performClick()
        compose.onNodeWithText("Code Solution").assertExists()
        compose.onNodeWithText("val count = « Blank 1 »", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Focus blank").performScrollTo().performClick()
        compose.onNodeWithText("Context for blank 2").assertExists()

        compose.onNodeWithContentDescription("Blank 1, unanswered").performScrollTo().performClick()
        compose.onAllNodes(radioButtonMatcher, useUnmergedTree = true)[1].performScrollTo().performClick()
        compose.onNodeWithContentDescription("Blank 2, answered").assertExists()
        compose.onNodeWithText("Full scaffold (24 lines)").performScrollTo().performClick()

        compose.runOnIdle {
            feedback = listOf(
                CodePuzzleBlankFeedback("____1", "20", false, "Incorrect"),
                CodePuzzleBlankFeedback("____2", "42", true, "Correct")
            )
            checked = true
        }

        // A graded result focuses the first incorrect blank, with feedback and explanation visible.
        compose.onNodeWithText("Blank 1 of 2").assertExists()
        compose.onNodeWithText("Context for blank 1").assertExists()
        compose.onNodeWithText("Full scaffold (24 lines)").assertExists()
        compose.onNodeWithText("Incorrect").assertExists()
        compose.onNodeWithText("The total function returns the selected count.").assertExists()
        val checkedChoices = compose.onAllNodes(radioButtonMatcher, useUnmergedTree = true)
        checkedChoices.assertCountEquals(4)
        checkedChoices[1].assertIsSelected()
        checkedChoices[1].assertIsNotEnabled()
    }
}
