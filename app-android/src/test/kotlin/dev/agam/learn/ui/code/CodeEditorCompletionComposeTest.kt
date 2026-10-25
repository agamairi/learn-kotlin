package dev.agam.learn.ui.code

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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
class CodeEditorCompletionComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editorDisplaysCompletionBarAndInsertsSuggestionOnTap() {
        val initial = TextFieldValue("val x = pri", TextRange(11))
        val editorValue = mutableStateOf(initial)

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()

        // Verify suggestion chip for println appears
        val printlnChip = compose.onNodeWithContentDescription("Suggestion: println")
        printlnChip.assertIsDisplayed()

        // Tap suggestion chip
        printlnChip.performClick()
        compose.waitForIdle()

        // Verify editor text is updated and cursor is placed inside parentheses
        assertEquals("val x = println()", editorValue.value.text)
        assertEquals(TextRange(16), editorValue.value.selection)
    }

    @Test
    fun memberCompletionsAppearAfterDot() {
        val initial = TextFieldValue("val s = \"hi\"\ns.", TextRange(15))
        val editorValue = mutableStateOf(initial)

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()

        // Verify member suggestions appear
        val lengthChip = compose.onNodeWithContentDescription("Suggestion: length")
        lengthChip.assertIsDisplayed()

        lengthChip.performClick()
        compose.waitForIdle()

        assertTrue(editorValue.value.text.endsWith("s.length"))
    }

    @Test
    fun incompleteStringConversionShowsMatchingMemberCompletion() {
        val source = "fun formatCents(cents: Long): String = TODO()\n\n" +
            "fun parseCents(text: String): Long? {\n    return text.toLongO"
        val editorValue = mutableStateOf(TextFieldValue(source, TextRange(source.length)))

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Suggestion: toLongOrNull").assertIsDisplayed()
    }

    @Test
    fun explicitCompletionCommitsLatinImeCompositionAndShowsSuggestions() {
        val source = "fun formatCents(cents: Long): String = TODO()\n\n" +
            "fun parseCents(text: String): Long? {\n    return text.toLongO"
        val compositionStart = source.lastIndexOf("toLongO")
        val editorValue = mutableStateOf(
            TextFieldValue(
                source,
                TextRange(source.length),
                TextRange(compositionStart, source.length)
            )
        )

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Suggestion: toLongOrNull").assertDoesNotExist()

        compose.onNodeWithContentDescription("Trigger code completion").performClick()
        compose.waitForIdle()

        assertEquals(source, editorValue.value.text)
        assertEquals(null, editorValue.value.composition)
        compose.onNodeWithContentDescription("Suggestion: toLongOrNull").assertIsDisplayed()
    }

    @Test
    fun explicitCompletionSurvivesImeCompositionReassertionAndPreservesSuffix() {
        val source = "val x = pri + 1"
        val composition = TextRange(8, 11)
        val editorValue = mutableStateOf(
            TextFieldValue(source, TextRange(11), composition)
        )

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Suggestion: println").assertDoesNotExist()

        compose.onNodeWithContentDescription("Trigger code completion").performClick()
        compose.waitForIdle()
        assertEquals(null, editorValue.value.composition)

        // Android IMEs may send the same pre-edit range again after the editor clears it.
        editorValue.value = editorValue.value.copy(composition = composition)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Suggestion: println").assertIsDisplayed()

        compose.onNodeWithContentDescription("Suggestion: println").performClick()
        compose.waitForIdle()

        assertEquals("val x = println() + 1", editorValue.value.text)
        assertEquals(TextRange(16), editorValue.value.selection)
        assertEquals(null, editorValue.value.composition)
    }

    @Test
    fun automaticCompletionRemainsHiddenDuringCjkImeComposition() {
        val source = "val x = pri中"
        val composingCharacter = source.indexOf('中')
        val editorValue = mutableStateOf(
            TextFieldValue(
                source,
                TextRange(composingCharacter),
                TextRange(composingCharacter, composingCharacter + 1)
            )
        )
        assertTrue(
            KotlinCompletionEngine.getCompletions(source, TextRange(composingCharacter))
                .any { it.label == "println" }
        )

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Suggestion: println").assertDoesNotExist()
    }

    @Test
    fun dismissButtonHidesCompletionBar() {
        val initial = TextFieldValue("val x = pri", TextRange(11))
        val editorValue = mutableStateOf(initial)

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Suggestion: println").assertIsDisplayed()
        val dismissBtn = compose.onNodeWithContentDescription("Dismiss completions")
        dismissBtn.performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Suggestion: println").assertDoesNotExist()
    }

    @Test
    fun explicitTriggerChipInSymbolBarActivatesCompletions() {
        val initial = TextFieldValue("val total = 10\n", TextRange(15))
        val editorValue = mutableStateOf(initial)

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()

        // Initially no prefix, so completions not displayed
        compose.onNodeWithContentDescription("Suggestion: total").assertDoesNotExist()

        // Tap the explicit completion chip in SymbolBar
        val triggerChip = compose.onNodeWithContentDescription("Trigger code completion")
        triggerChip.performClick()
        compose.waitForIdle()

        // Now total should be displayed as a suggestion
        compose.onNodeWithContentDescription("Suggestion: total").assertIsDisplayed()
    }

    @Test
    fun readOnlyEditorSuppressesCompletionBar() {
        val initial = TextFieldValue("val x = pri", TextRange(11))
        val editorValue = mutableStateOf(initial)

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it },
                        readOnly = true
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Suggestion: println").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dismiss completions").assertDoesNotExist()
    }

    @Test
    fun nonCollapsedSelectionSuppressesCompletionBar() {
        // Selection highlighted
        val initial = TextFieldValue("val x = pri", TextRange(8, 11))
        val editorValue = mutableStateOf(initial)

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Suggestion: println").assertDoesNotExist()
    }

    @Test
    fun sourceChangesRefreshSuggestionsEvenWhenCursorContextIsEqual() {
        val initial = "val foo = 1\npri"
        val editorValue = mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Suggestion: pri").assertDoesNotExist()

        val updated = "val pri = 1\npri"
        compose.runOnIdle {
            editorValue.value = TextFieldValue(updated, TextRange(updated.length))
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Suggestion: pri").assertIsDisplayed()
    }

    @Test
    fun movingCursorAfterDismissalReenablesSuggestionsAtTheNewLocation() {
        val initial = "val total = 10\nval x = pri\nval y = tot"
        val editorValue = mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dismiss completions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Suggestion: total").assertDoesNotExist()

        compose.runOnIdle {
            editorValue.value = editorValue.value.copy(selection = TextRange(initial.indexOf("pri") + 3))
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Suggestion: println").assertIsDisplayed()
    }

    @Test
    fun completionIsHiddenDuringImeCompositionAndExplicitRequestExpiresOnCursorMove() {
        val initial = "val total = 10\n"
        val editorValue = mutableStateOf(
            TextFieldValue(initial, TextRange(initial.length), TextRange(initial.length))
        )

        compose.setContent {
            LearnKotlinTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    CodeEditor(
                        value = editorValue.value,
                        onValueChange = { editorValue.value = it }
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Suggestion: total").assertDoesNotExist()

        compose.runOnIdle {
            editorValue.value = editorValue.value.copy(composition = null)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Trigger code completion").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Suggestion: total").assertIsDisplayed()

        compose.runOnIdle {
            editorValue.value = editorValue.value.copy(selection = TextRange(initial.indexOf("val") + 4))
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Suggestion: total").assertDoesNotExist()
    }
}
