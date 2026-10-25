package dev.agam.learn.ui.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinCompletionEngineTest {

    @Test
    fun keywordCompletionsMatchPrefix() {
        val text = "fu"
        val completions = KotlinCompletionEngine.getCompletions(text, TextRange(2))
        assertTrue(completions.any { it.label == "fun" && it.kind == CompletionKind.KEYWORD })
        assertEquals("fun ", completions.first { it.label == "fun" }.insertText)
    }

    @Test
    fun standardFunctionCompletionsMatchPrefix() {
        val text = "pri"
        val completions = KotlinCompletionEngine.getCompletions(text, TextRange(3))
        val printlnItem = completions.firstOrNull { it.label == "println" }
        assertTrue("println should be suggested for prefix 'pri'", printlnItem != null)
        assertEquals(CompletionKind.FUNCTION, printlnItem?.kind)
        assertEquals("println()", printlnItem?.insertText)
        assertEquals(8, printlnItem?.cursorOffset) // inside parentheses
    }

    @Test
    fun standardTypeCompletionsMatchPrefix() {
        val text = "In"
        val completions = KotlinCompletionEngine.getCompletions(text, TextRange(2))
        assertTrue(completions.any { it.label == "Int" && it.kind == CompletionKind.TYPE })
    }

    @Test
    fun memberCompletionsTriggerImmediatelyAfterDot() {
        val text = "val list = listOf(1, 2)\nlist."
        val completions = KotlinCompletionEngine.getCompletions(text, TextRange(text.length))
        assertTrue("Completions should be available after dot", completions.isNotEmpty())
        assertTrue("filter should be suggested for collection", completions.any { it.label == "filter" })
        assertTrue("map should be suggested for collection", completions.any { it.label == "map" })
        assertTrue("size should be suggested for collection", completions.any { it.label == "size" })
    }

    @Test
    fun memberCompletionsFilterByPrefix() {
        val text = "val list = listOf(\"hello\")\nlist.fi"
        val completions = KotlinCompletionEngine.getCompletions(text, TextRange(text.length))
        assertTrue(completions.any { it.label == "filter" })
        assertTrue(completions.any { it.label == "first" })
        assertFalse(completions.any { it.label == "length" })
    }

    @Test
    fun memberCompletionsUseObviousReceiverTypeWithoutClaimingUnknownTypes() {
        val stringSource = "val s = \"hello\"\ns."
        val stringCompletions = KotlinCompletionEngine.getCompletions(stringSource, TextRange(stringSource.length))
        assertTrue(stringCompletions.any { it.label == "length" })
        assertFalse(stringCompletions.any { it.label == "filter" })

        val unknownSource = "someObject."
        val unknownCompletions = KotlinCompletionEngine.getCompletions(unknownSource, TextRange(unknownSource.length))
        assertTrue(unknownCompletions.any { it.label == "toString" })
        assertFalse(unknownCompletions.any { it.label == "filter" })
        assertFalse(unknownCompletions.any { it.label == "length" })

        val chainedSource = "val bar = \"hello\"\nfoo.bar."
        val chainedCompletions = KotlinCompletionEngine.getCompletions(
            chainedSource,
            TextRange(chainedSource.length)
        )
        assertFalse(chainedCompletions.any { it.label == "length" })
    }

    @Test
    fun userDeclaredVariablesAndFunctionsAreExtractedAndPrioritized() {
        val source = """
            fun calculateTotal(rate: Double): Long {
                val baseAmount = 1000L
                val bas
            }
        """.trimIndent()
        val basIndex = source.lastIndexOf("val bas") + "val bas".length
        val completions = KotlinCompletionEngine.getCompletions(source, TextRange(basIndex))

        val baseAmountItem = completions.firstOrNull { it.label == "baseAmount" }
        assertTrue("baseAmount should be suggested from local source", baseAmountItem != null)
        assertEquals(CompletionKind.VARIABLE, baseAmountItem?.kind)
        assertEquals(100, baseAmountItem?.priority)
    }

    @Test
    fun userDeclaredFunctionParametersAreSuggested() {
        val source = """
            fun processOrders(orderList: List<Order>, maxLimit: Int) {
                ord
            }
        """.trimIndent()
        val ordIndex = source.lastIndexOf("ord") + 3
        val completions = KotlinCompletionEngine.getCompletions(source, TextRange(ordIndex))

        val paramItem = completions.firstOrNull { it.label == "orderList" }
        assertTrue("orderList parameter should be suggested", paramItem != null)
        assertEquals(CompletionKind.PARAMETER, paramItem?.kind)
    }

    @Test
    fun stringParameterCompletesIncompleteConversionCall() {
        val source = "fun parseCents(text: String): Long? {\n    return text.toLongO"
        val context = KotlinCompletionEngine.analyzeContext(source, TextRange(source.length))
        assertTrue(context.isMember)
        assertEquals("text", context.receiver)
        val completions = KotlinCompletionEngine.getCompletions(source, TextRange(source.length))

        assertTrue(completions.any { it.label == "toLongOrNull" })
    }

    @Test
    fun userDeclaredClassesAndPropertiesAreExtracted() {
        val source = """
            data class Transaction(val id: String, val amount: Long)
            val tx = Transaction("1", 50)
            tx.am
        """.trimIndent()
        val amIndex = source.lastIndexOf("tx.am") + 5
        val completions = KotlinCompletionEngine.getCompletions(source, TextRange(amIndex))

        val amountProp = completions.firstOrNull { it.label == "amount" }
        assertTrue("Constructor property amount should be suggested", amountProp != null)
        assertEquals(CompletionKind.PROPERTY, amountProp?.kind)
    }

    @Test
    fun destructuringAndForLoopsAndLambdasExtractVariables() {
        val source = """
            val (key, value) = pair
            for (record in records) {
                items.forEach { element ->
                }
            }
        """.trimIndent()
        val decls = KotlinCompletionEngine.extractDeclarations(source)
        val labels = decls.map { it.label }.toSet()
        assertTrue("Destructured key should be extracted", labels.contains("key"))
        assertTrue("Destructured value should be extracted", labels.contains("value"))
        assertTrue("For loop variable record should be extracted", labels.contains("record"))
        assertTrue("Lambda parameter element should be extracted", labels.contains("element"))
    }

    @Test
    fun completionsSuppressedInsideLineComments() {
        val text = "// val x = pri"
        val completions = KotlinCompletionEngine.getCompletions(text, TextRange(text.length))
        assertTrue("Completions should be suppressed inside line comments", completions.isEmpty())
    }

    @Test
    fun completionsSuppressedInsideBlockComments() {
        val text = "/* pri */"
        val completions = KotlinCompletionEngine.getCompletions(text, TextRange(5))
        assertTrue("Completions should be suppressed inside block comments", completions.isEmpty())
    }

    @Test
    fun completionsSuppressedInsideStringLiteralsExceptTemplates() {
        val normalString = "val msg = \"hello pri\""
        val priPos = normalString.indexOf("pri") + 3
        val normalCompletions = KotlinCompletionEngine.getCompletions(normalString, TextRange(priPos))
        assertTrue("Completions should be suppressed in plain string literals", normalCompletions.isEmpty())

        val templateString = "val msg = \"hello \${pri\""
        val templatePriPos = templateString.indexOf("pri") + 3
        val templateCompletions = KotlinCompletionEngine.getCompletions(templateString, TextRange(templatePriPos))
        assertTrue("Completions should be permitted inside string template expression \${...}", templateCompletions.isNotEmpty())
        assertTrue(templateCompletions.any { it.label == "println" })
    }

    @Test
    fun nonCollapsedSelectionSuppressesCompletions() {
        val text = "val total = 100"
        val selection = TextRange(4, 9) // "total" highlighted
        val context = KotlinCompletionEngine.analyzeContext(text, selection)
        assertTrue(context.hasSelection)
        assertFalse(context.canComplete)
        val completions = KotlinCompletionEngine.getCompletions(text, selection)
        assertTrue("No completions when text is actively selected", completions.isEmpty())
    }

    @Test
    fun cursorMiddleOfLineReplacesPrefixAndPositionsCursorSensibly() {
        // Cursor is in the middle of a line: "val x = pri + 10" with cursor right after "pri"
        val text = "val x = pri + 10"
        val cursor = 11 // right after "pri"
        val initial = TextFieldValue(text = text, selection = TextRange(cursor))

        val context = KotlinCompletionEngine.analyzeContext(text, initial.selection)
        assertEquals("pri", context.prefix)
        assertEquals(8, context.prefixStart)

        val printlnItem = KotlinCompletionEngine.STANDARD_FUNCTIONS.first { it.label == "println" }
        val updated = KotlinCompletionEngine.applyCompletion(initial, printlnItem, context)

        // "pri" replaced by "println()", suffix " + 10" preserved
        assertEquals("val x = println() + 10", updated.text)
        // Cursor placed inside parentheses: "println(" is 8 chars, 8 + 8 = 16
        assertEquals(TextRange(16), updated.selection)
        assertEquals('(', updated.text[15])
        assertEquals(')', updated.text[16])
    }

    @Test
    fun cursorMiddleOfWordReplacesPrefix() {
        val text = "val total = 10\nval x = tot"
        val cursor = text.length
        val initial = TextFieldValue(text = text, selection = TextRange(cursor))
        val context = KotlinCompletionEngine.analyzeContext(text, initial.selection)

        val totalItem = CompletionItem("total", "total", 5, CompletionKind.VARIABLE)
        val updated = KotlinCompletionEngine.applyCompletion(initial, totalItem, context)

        assertEquals("val total = 10\nval x = total", updated.text)
        assertEquals(TextRange(updated.text.length), updated.selection)
    }

    @Test
    fun staleCompletionContextUsesTheCurrentCursorRange() {
        val text = "val first = pri\nval second = pri + 10"
        val firstPrefixEnd = text.indexOf("pri") + 3
        val staleContext = KotlinCompletionEngine.analyzeContext(text, TextRange(firstPrefixEnd))
        val currentValue = TextFieldValue(text, TextRange(text.lastIndexOf("pri") + 3))
        val printlnItem = KotlinCompletionEngine.STANDARD_FUNCTIONS.first { it.label == "println" }

        val updated = KotlinCompletionEngine.applyCompletion(currentValue, printlnItem, staleContext)

        assertEquals("val first = pri\nval second = println() + 10", updated.text)
        assertEquals(TextRange(text.lastIndexOf("pri") + 8), updated.selection)
    }

    @Test
    fun completionDoesNotReplaceAnActiveImeComposition() {
        val composing = TextFieldValue(
            text = "val x = pri",
            selection = TextRange(11),
            composition = TextRange(8, 11)
        )
        val printlnItem = KotlinCompletionEngine.STANDARD_FUNCTIONS.first { it.label == "println" }

        assertEquals(composing, KotlinCompletionEngine.applyCompletion(composing, printlnItem))
    }

    @Test
    fun collapsedImeCompositionDoesNotBlockCompletion() {
        val value = TextFieldValue(
            text = "val x = pri",
            selection = TextRange(11),
            composition = TextRange(11)
        )
        val printlnItem = KotlinCompletionEngine.STANDARD_FUNCTIONS.first { it.label == "println" }

        val updated = KotlinCompletionEngine.applyCompletion(value, printlnItem)

        assertEquals("val x = println()", updated.text)
        assertEquals(TextRange(16), updated.selection)
    }

    @Test
    fun snippetCursorPlacements() {
        val initial = TextFieldValue("list.fil", TextRange("list.fil".length))
        val context = KotlinCompletionEngine.analyzeContext(initial.text, initial.selection)
        val filterItem = KotlinCompletionEngine.MEMBER_SUGGESTIONS.first { it.label == "filter" }

        val updated = KotlinCompletionEngine.applyCompletion(initial, filterItem, context)
        assertEquals("list.filter {  }", updated.text)
        // cursorOffset is 9 -> right between the spaces inside braces: "filter { | }"
        assertEquals(TextRange(14), updated.selection)
        assertEquals(' ', updated.text[13])
        assertEquals(' ', updated.text[14])
    }

    @Test
    fun largeTextPerformanceAndAccurateReplacement() {
        val sb = StringBuilder()
        for (i in 1..200) {
            sb.append("data class Model$i(val id$i: String, val count$i: Int)\n")
            sb.append("fun calculate$i(param$i: Int): Int = param$i * 2\n")
        }
        sb.append("val finalResult = pri")

        val source = sb.toString()
        val cursor = source.length
        val initial = TextFieldValue(source, TextRange(cursor))

        val startTime = System.currentTimeMillis()
        val completions = KotlinCompletionEngine.getCompletions(source, TextRange(cursor))
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Analysis should execute in under 100 ms for large source", elapsed < 100)
        assertTrue(completions.any { it.label == "println" })

        val printlnItem = completions.first { it.label == "println" }
        val updated = KotlinCompletionEngine.applyCompletion(initial, printlnItem)
        assertTrue(updated.text.endsWith("val finalResult = println()"))
        assertEquals(updated.text.length - 1, updated.selection.end)
    }
}
