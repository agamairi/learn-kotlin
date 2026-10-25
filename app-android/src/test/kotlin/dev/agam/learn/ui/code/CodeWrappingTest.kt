package dev.agam.learn.ui.code

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.agam.learn.ui.components.CodeBlock
import dev.agam.learn.ui.theme.LearnKotlinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CodeWrappingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun codeBlockWrapsLongTokensWithinItsNarrowWidth() {
        val source = "x".repeat(80)

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(180.dp)) {
                    CodeBlock(code = source)
                }
            }
        }

        val textNode = compose.onNodeWithText(source, useUnmergedTree = true)
        val layout = textLayout(textNode)
        assertEquals(source, layout.layoutInput.text.text)
        assertTrue("long token should occupy multiple visual rows", layout.lineCount > 1)
    }

    @Test
    fun editorWrapsVisuallyKeepsSelectionAndAlignsLogicalLineNumbers() {
        val token = "x".repeat(80)
        val source = "val first = $token\nval second = 2"
        val initialValue = TextFieldValue(
            text = source,
            selection = TextRange(5, 12)
        )
        val editorValue = mutableStateOf(initialValue)

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(220.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()

        val editorNode = compose.onNodeWithContentDescription("Code editor")
        val layout = textLayout(editorNode)
        val secondLineOffset = source.indexOf('\n') + 1
        val secondVisualLine = layout.getLineForOffset(secondLineOffset)
        val tokenStart = source.indexOf(token)
        val firstTokenVisualLine = layout.getLineForOffset(tokenStart)
        val lastTokenVisualLine = layout.getLineForOffset(tokenStart + token.lastIndex)

        assertEquals(source, layout.layoutInput.text.text)
        assertTrue("long token should wrap across visual rows", lastTokenVisualLine > firstTokenVisualLine)
        assertTrue("second logical line should follow the wrapped token", secondVisualLine > lastTokenVisualLine)
        compose.runOnIdle {
            assertEquals(initialValue, editorValue.value)
        }

        val secondLineNumber = compose.onNodeWithText("2", useUnmergedTree = true)
            .fetchSemanticsNode()
        val editorBounds = editorNode.fetchSemanticsNode().boundsInRoot
        val expectedTop = editorBounds.top + layout.getLineTop(secondVisualLine)
        assertTrue(
            "logical line number should start beside its first visual row",
            kotlin.math.abs(secondLineNumber.boundsInRoot.top - expectedTop) <= compose.density.density * 1.5f
        )
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("text layout semantics action should be present", action != null)
        assertTrue("text layout should be available", action!!.invoke(results))
        return results.single()
    }
}
