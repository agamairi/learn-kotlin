package dev.agam.learn.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agam.learn.ui.theme.ExtendedColors
import dev.agam.learn.ui.theme.extendedColors

/**
 * Categories of code completion items.
 */
enum class CompletionKind {
    KEYWORD,
    FUNCTION,
    VARIABLE,
    PARAMETER,
    PROPERTY,
    TYPE,
    MEMBER,
    SNIPPET
}

/**
 * A single completion suggestion presented to the user.
 *
 * @param label The text displayed on the suggestion chip.
 * @param insertText The actual text to insert into the editor when selected.
 * @param cursorOffset The relative cursor position within [insertText] after insertion.
 * @param kind The grammatical category of this suggestion.
 * @param detail Short secondary hint (e.g. return type, parameter signature).
 * @param priority Relative ranking weight (higher values sort first).
 */
data class CompletionItem(
    val label: String,
    val insertText: String = label,
    val cursorOffset: Int = insertText.length,
    val kind: CompletionKind,
    val detail: String? = null,
    val priority: Int = 0
)

/**
 * Analyzed syntax context around the current cursor/selection in the editor.
 */
data class CompletionContext(
    val prefix: String,
    val prefixStart: Int,
    val cursor: Int,
    val isMember: Boolean,
    val receiver: String?,
    val isInsideComment: Boolean,
    val isInsideString: Boolean,
    val hasSelection: Boolean
) {
    val canComplete: Boolean
        get() = !hasSelection && !isInsideComment && !isInsideString
}

internal val TextFieldValue.hasActiveComposition: Boolean
    get() = composition?.let { it.min != it.max } == true

/**
 * Fast, offline Kotlin lexical completion engine. This is a convenience heuristic,
 * not compiler-backed resolution or typed IntelliSense.
 *
 * Provides contextual code completion by analyzing:
 * 1. User-declared symbols in the active editor source (classes, functions, parameters, variables, properties).
 * 2. Kotlin keywords, standard control structures, and snippets.
 * 3. Kotlin standard types (primitives, collections, functional types).
 * 4. Kotlin standard library functions and member methods/properties (e.g. collection and string operations).
 */
object KotlinCompletionEngine {

    val STANDARD_KEYWORDS: List<CompletionItem> = listOf(
        CompletionItem("fun", "fun ", 4, CompletionKind.KEYWORD, "function declaration", priority = 70),
        CompletionItem("val", "val ", 4, CompletionKind.KEYWORD, "read-only property", priority = 70),
        CompletionItem("var", "var ", 4, CompletionKind.KEYWORD, "mutable variable", priority = 70),
        CompletionItem("class", "class ", 6, CompletionKind.KEYWORD, "class declaration", priority = 65),
        CompletionItem("data class", "data class ", 11, CompletionKind.KEYWORD, "data class declaration", priority = 65),
        CompletionItem("interface", "interface ", 10, CompletionKind.KEYWORD, "interface declaration", priority = 65),
        CompletionItem("object", "object ", 7, CompletionKind.KEYWORD, "singleton object", priority = 65),
        CompletionItem("return", "return ", 7, CompletionKind.KEYWORD, "return statement", priority = 75),
        CompletionItem("if", "if ()", 4, CompletionKind.KEYWORD, "conditional", priority = 70),
        CompletionItem("else", "else ", 5, CompletionKind.KEYWORD, "else branch", priority = 70),
        CompletionItem("when", "when ()", 6, CompletionKind.KEYWORD, "pattern match", priority = 70),
        CompletionItem("for", "for ( in )", 5, CompletionKind.KEYWORD, "for loop", priority = 70),
        CompletionItem("while", "while ()", 7, CompletionKind.KEYWORD, "while loop", priority = 65),
        CompletionItem("is", "is ", 3, CompletionKind.KEYWORD, "type check", priority = 60),
        CompletionItem("!is", "!is ", 4, CompletionKind.KEYWORD, "negated type check", priority = 60),
        CompletionItem("in", "in ", 3, CompletionKind.KEYWORD, "membership check", priority = 60),
        CompletionItem("!in", "!in ", 4, CompletionKind.KEYWORD, "negated membership check", priority = 60),
        CompletionItem("as", "as ", 3, CompletionKind.KEYWORD, "type cast", priority = 60),
        CompletionItem("as?", "as? ", 4, CompletionKind.KEYWORD, "safe type cast", priority = 60),
        CompletionItem("override", "override ", 9, CompletionKind.KEYWORD, "override modifier", priority = 65),
        CompletionItem("private", "private ", 8, CompletionKind.KEYWORD, "visibility modifier", priority = 65),
        CompletionItem("public", "public ", 7, CompletionKind.KEYWORD, "visibility modifier", priority = 60),
        CompletionItem("protected", "protected ", 10, CompletionKind.KEYWORD, "visibility modifier", priority = 60),
        CompletionItem("internal", "internal ", 9, CompletionKind.KEYWORD, "visibility modifier", priority = 60),
        CompletionItem("try", "try ", 4, CompletionKind.KEYWORD, "exception block", priority = 60),
        CompletionItem("catch", "catch ()", 7, CompletionKind.KEYWORD, "exception handler", priority = 60),
        CompletionItem("finally", "finally ", 8, CompletionKind.KEYWORD, "finally block", priority = 60),
        CompletionItem("throw", "throw ", 6, CompletionKind.KEYWORD, "throw exception", priority = 60),
        CompletionItem("true", "true", 4, CompletionKind.KEYWORD, "boolean literal", priority = 70),
        CompletionItem("false", "false", 5, CompletionKind.KEYWORD, "boolean literal", priority = 70),
        CompletionItem("null", "null", 4, CompletionKind.KEYWORD, "null literal", priority = 70),
        CompletionItem("this", "this", 4, CompletionKind.KEYWORD, "current instance", priority = 60),
        CompletionItem("super", "super", 5, CompletionKind.KEYWORD, "super type", priority = 60),
        CompletionItem("companion object", "companion object ", 17, CompletionKind.KEYWORD, "companion object", priority = 60)
    )

    val STANDARD_TYPES: List<CompletionItem> = listOf(
        CompletionItem("Int", "Int", 3, CompletionKind.TYPE, "32-bit integer", priority = 75),
        CompletionItem("String", "String", 6, CompletionKind.TYPE, "character sequence", priority = 75),
        CompletionItem("Boolean", "Boolean", 7, CompletionKind.TYPE, "true or false", priority = 75),
        CompletionItem("Long", "Long", 4, CompletionKind.TYPE, "64-bit integer", priority = 75),
        CompletionItem("Double", "Double", 6, CompletionKind.TYPE, "64-bit float", priority = 75),
        CompletionItem("Float", "Float", 5, CompletionKind.TYPE, "32-bit float", priority = 70),
        CompletionItem("Char", "Char", 4, CompletionKind.TYPE, "character", priority = 70),
        CompletionItem("Byte", "Byte", 4, CompletionKind.TYPE, "8-bit integer", priority = 65),
        CompletionItem("Short", "Short", 5, CompletionKind.TYPE, "16-bit integer", priority = 65),
        CompletionItem("Any", "Any", 3, CompletionKind.TYPE, "root Kotlin type", priority = 70),
        CompletionItem("Unit", "Unit", 4, CompletionKind.TYPE, "unit return", priority = 75),
        CompletionItem("Nothing", "Nothing", 7, CompletionKind.TYPE, "bottom type", priority = 65),
        CompletionItem("List", "List", 4, CompletionKind.TYPE, "immutable ordered list", priority = 80),
        CompletionItem("MutableList", "MutableList", 11, CompletionKind.TYPE, "mutable ordered list", priority = 80),
        CompletionItem("Map", "Map", 3, CompletionKind.TYPE, "key-value map", priority = 80),
        CompletionItem("MutableMap", "MutableMap", 10, CompletionKind.TYPE, "mutable key-value map", priority = 80),
        CompletionItem("Set", "Set", 3, CompletionKind.TYPE, "unique element collection", priority = 80),
        CompletionItem("MutableSet", "MutableSet", 10, CompletionKind.TYPE, "mutable set", priority = 80),
        CompletionItem("Array", "Array", 5, CompletionKind.TYPE, "fixed-size array", priority = 70),
        CompletionItem("Pair", "Pair", 4, CompletionKind.TYPE, "generic 2-tuple", priority = 75),
        CompletionItem("Triple", "Triple", 6, CompletionKind.TYPE, "generic 3-tuple", priority = 70),
        CompletionItem("Sequence", "Sequence", 8, CompletionKind.TYPE, "lazy evaluation", priority = 70),
        CompletionItem("Iterable", "Iterable", 8, CompletionKind.TYPE, "iterable elements", priority = 70),
        CompletionItem("Result", "Result", 6, CompletionKind.TYPE, "discriminated union", priority = 65),
        CompletionItem("Exception", "Exception", 9, CompletionKind.TYPE, "base exception", priority = 65),
        CompletionItem("IllegalArgumentException", "IllegalArgumentException", 24, CompletionKind.TYPE, "argument error", priority = 65),
        CompletionItem("IllegalStateException", "IllegalStateException", 21, CompletionKind.TYPE, "state error", priority = 65)
    )

    val STANDARD_FUNCTIONS: List<CompletionItem> = listOf(
        CompletionItem("println", "println()", 8, CompletionKind.FUNCTION, "(message: Any?): Unit", priority = 85),
        CompletionItem("print", "print()", 6, CompletionKind.FUNCTION, "(message: Any?): Unit", priority = 80),
        CompletionItem("listOf", "listOf()", 7, CompletionKind.FUNCTION, "(vararg elements: T): List<T>", priority = 85),
        CompletionItem("mutableListOf", "mutableListOf()", 14, CompletionKind.FUNCTION, "(vararg elements: T): MutableList<T>", priority = 85),
        CompletionItem("mapOf", "mapOf()", 6, CompletionKind.FUNCTION, "(vararg pairs: Pair<K, V>): Map<K, V>", priority = 85),
        CompletionItem("mutableMapOf", "mutableMapOf()", 13, CompletionKind.FUNCTION, "(): MutableMap<K, V>", priority = 80),
        CompletionItem("setOf", "setOf()", 6, CompletionKind.FUNCTION, "(vararg elements: T): Set<T>", priority = 80),
        CompletionItem("mutableSetOf", "mutableSetOf()", 13, CompletionKind.FUNCTION, "(): MutableSet<T>", priority = 80),
        CompletionItem("emptyList", "emptyList()", 11, CompletionKind.FUNCTION, "(): List<T>", priority = 85),
        CompletionItem("emptyMap", "emptyMap()", 10, CompletionKind.FUNCTION, "(): Map<K, V>", priority = 85),
        CompletionItem("emptySet", "emptySet()", 10, CompletionKind.FUNCTION, "(): Set<T>", priority = 80),
        CompletionItem("arrayOf", "arrayOf()", 8, CompletionKind.FUNCTION, "(vararg elements: T): Array<T>", priority = 75),
        CompletionItem("buildList", "buildList {\n    \n}", 15, CompletionKind.SNIPPET, "builder for List", priority = 75),
        CompletionItem("buildMap", "buildMap {\n    \n}", 14, CompletionKind.SNIPPET, "builder for Map", priority = 75),
        CompletionItem("buildString", "buildString {\n    \n}", 17, CompletionKind.SNIPPET, "builder for String", priority = 75),
        CompletionItem("require", "require()", 8, CompletionKind.FUNCTION, "(value: Boolean): Unit", priority = 75),
        CompletionItem("requireNotNull", "requireNotNull()", 15, CompletionKind.FUNCTION, "(value: T?): T", priority = 75),
        CompletionItem("check", "check()", 6, CompletionKind.FUNCTION, "(value: Boolean): Unit", priority = 75),
        CompletionItem("checkNotNull", "checkNotNull()", 13, CompletionKind.FUNCTION, "(value: T?): T", priority = 75),
        CompletionItem("error", "error()", 6, CompletionKind.FUNCTION, "(message: Any): Nothing", priority = 70),
        CompletionItem("TODO", "TODO()", 6, CompletionKind.FUNCTION, "(): Nothing", priority = 80),
        CompletionItem("repeat", "repeat() {  }", 7, CompletionKind.SNIPPET, "(times: Int) { action }", priority = 70),
        CompletionItem("maxOf", "maxOf()", 6, CompletionKind.FUNCTION, "(a, b): Comparable", priority = 75),
        CompletionItem("minOf", "minOf()", 6, CompletionKind.FUNCTION, "(a, b): Comparable", priority = 75)
    )

    val MEMBER_SUGGESTIONS: List<CompletionItem> = listOf(
        // Collection & Iterable operations
        CompletionItem("filter", "filter {  }", 9, CompletionKind.MEMBER, "predicate: (T) -> Boolean", priority = 90),
        CompletionItem("filterNot", "filterNot {  }", 12, CompletionKind.MEMBER, "predicate: (T) -> Boolean", priority = 80),
        CompletionItem("filterNotNull", "filterNotNull()", 16, CompletionKind.MEMBER, "(): List<T>", priority = 85),
        CompletionItem("map", "map {  }", 6, CompletionKind.MEMBER, "transform: (T) -> R", priority = 90),
        CompletionItem("mapNotNull", "mapNotNull {  }", 13, CompletionKind.MEMBER, "transform: (T) -> R?", priority = 85),
        CompletionItem("flatMap", "flatMap {  }", 10, CompletionKind.MEMBER, "transform: (T) -> Iterable<R>", priority = 85),
        CompletionItem("forEach", "forEach {  }", 10, CompletionKind.MEMBER, "action: (T) -> Unit", priority = 85),
        CompletionItem("first", "first()", 7, CompletionKind.MEMBER, "(): T", priority = 85),
        CompletionItem("firstOrNull", "firstOrNull()", 13, CompletionKind.MEMBER, "(): T?", priority = 85),
        CompletionItem("last", "last()", 6, CompletionKind.MEMBER, "(): T", priority = 80),
        CompletionItem("lastOrNull", "lastOrNull()", 12, CompletionKind.MEMBER, "(): T?", priority = 80),
        CompletionItem("find", "find {  }", 7, CompletionKind.MEMBER, "predicate: (T) -> Boolean", priority = 80),
        CompletionItem("any", "any {  }", 6, CompletionKind.MEMBER, "predicate: (T) -> Boolean", priority = 80),
        CompletionItem("all", "all {  }", 6, CompletionKind.MEMBER, "predicate: (T) -> Boolean", priority = 80),
        CompletionItem("none", "none {  }", 7, CompletionKind.MEMBER, "predicate: (T) -> Boolean", priority = 75),
        CompletionItem("count", "count()", 7, CompletionKind.MEMBER, "(): Int", priority = 85),
        CompletionItem("sumOf", "sumOf {  }", 8, CompletionKind.MEMBER, "selector: (T) -> Long", priority = 85),
        CompletionItem("sorted", "sorted()", 8, CompletionKind.MEMBER, "(): List<T>", priority = 80),
        CompletionItem("sortedBy", "sortedBy {  }", 11, CompletionKind.MEMBER, "selector: (T) -> R", priority = 85),
        CompletionItem("sortedDescending", "sortedDescending()", 18, CompletionKind.MEMBER, "(): List<T>", priority = 75),
        CompletionItem("reversed", "reversed()", 10, CompletionKind.MEMBER, "(): List<T>", priority = 80),
        CompletionItem("distinct", "distinct()", 10, CompletionKind.MEMBER, "(): List<T>", priority = 80),
        CompletionItem("distinctBy", "distinctBy {  }", 13, CompletionKind.MEMBER, "selector: (T) -> K", priority = 80),
        CompletionItem("take", "take()", 5, CompletionKind.MEMBER, "(n: Int): List<T>", priority = 80),
        CompletionItem("drop", "drop()", 5, CompletionKind.MEMBER, "(n: Int): List<T>", priority = 80),
        CompletionItem("joinToString", "joinToString()", 14, CompletionKind.MEMBER, "(separator = \", \"): String", priority = 85),
        CompletionItem("contains", "contains()", 9, CompletionKind.MEMBER, "(element: T): Boolean", priority = 80),
        CompletionItem("containsKey", "containsKey()", 12, CompletionKind.MEMBER, "(key: K): Boolean", priority = 80),
        CompletionItem("containsValue", "containsValue()", 14, CompletionKind.MEMBER, "(value: V): Boolean", priority = 75),
        CompletionItem("get", "get()", 4, CompletionKind.MEMBER, "(index: Int): T", priority = 80),
        CompletionItem("getOrNull", "getOrNull()", 10, CompletionKind.MEMBER, "(index: Int): T?", priority = 85),
        CompletionItem("getOrDefault", "getOrDefault()", 13, CompletionKind.MEMBER, "(key: K, default: V): V", priority = 80),
        CompletionItem("size", "size", 4, CompletionKind.PROPERTY, "Int", priority = 90),
        CompletionItem("indices", "indices", 7, CompletionKind.PROPERTY, "IntRange", priority = 75),
        CompletionItem("lastIndex", "lastIndex", 9, CompletionKind.PROPERTY, "Int", priority = 75),
        CompletionItem("isEmpty", "isEmpty()", 9, CompletionKind.MEMBER, "(): Boolean", priority = 90),
        CompletionItem("isNotEmpty", "isNotEmpty()", 12, CompletionKind.MEMBER, "(): Boolean", priority = 90),
        CompletionItem("associate", "associate {  }", 12, CompletionKind.MEMBER, "transform: (T) -> Pair<K, V>", priority = 80),
        CompletionItem("associateBy", "associateBy {  }", 14, CompletionKind.MEMBER, "keySelector: (T) -> K", priority = 80),
        CompletionItem("groupBy", "groupBy {  }", 10, CompletionKind.MEMBER, "keySelector: (T) -> K", priority = 85),
        CompletionItem("chunked", "chunked()", 8, CompletionKind.MEMBER, "(size: Int): List<List<T>>", priority = 75),
        // String operations
        CompletionItem("length", "length", 6, CompletionKind.PROPERTY, "Int", priority = 90),
        CompletionItem("substring", "substring()", 10, CompletionKind.MEMBER, "(startIndex: Int): String", priority = 85),
        CompletionItem("startsWith", "startsWith()", 11, CompletionKind.MEMBER, "(prefix: String): Boolean", priority = 80),
        CompletionItem("endsWith", "endsWith()", 9, CompletionKind.MEMBER, "(suffix: String): Boolean", priority = 80),
        CompletionItem("lowercase", "lowercase()", 11, CompletionKind.MEMBER, "(): String", priority = 80),
        CompletionItem("uppercase", "uppercase()", 11, CompletionKind.MEMBER, "(): String", priority = 80),
        CompletionItem("trim", "trim()", 6, CompletionKind.MEMBER, "(): String", priority = 85),
        CompletionItem("split", "split()", 6, CompletionKind.MEMBER, "(delimiters: String): List<String>", priority = 85),
        CompletionItem("replace", "replace()", 8, CompletionKind.MEMBER, "(old: String, new: String): String", priority = 80),
        CompletionItem("matches", "matches()", 8, CompletionKind.MEMBER, "(regex: Regex): Boolean", priority = 75),
        CompletionItem("toInt", "toInt()", 7, CompletionKind.MEMBER, "(): Int", priority = 85),
        CompletionItem("toIntOrNull", "toIntOrNull()", 13, CompletionKind.MEMBER, "(): Int?", priority = 85),
        CompletionItem("toLong", "toLong()", 8, CompletionKind.MEMBER, "(): Long", priority = 85),
        CompletionItem("toLongOrNull", "toLongOrNull()", 14, CompletionKind.MEMBER, "(): Long?", priority = 85),
        CompletionItem("toDouble", "toDouble()", 10, CompletionKind.MEMBER, "(): Double", priority = 80),
        CompletionItem("toDoubleOrNull", "toDoubleOrNull()", 16, CompletionKind.MEMBER, "(): Double?", priority = 80),
        CompletionItem("isNullOrEmpty", "isNullOrEmpty()", 15, CompletionKind.MEMBER, "(): Boolean", priority = 85),
        CompletionItem("isNullOrBlank", "isNullOrBlank()", 15, CompletionKind.MEMBER, "(): Boolean", priority = 85),
        // Scope & general methods
        CompletionItem("let", "let {  }", 6, CompletionKind.MEMBER, "block: (T) -> R", priority = 85),
        CompletionItem("also", "also {  }", 7, CompletionKind.MEMBER, "block: (T) -> Unit", priority = 80),
        CompletionItem("apply", "apply {  }", 8, CompletionKind.MEMBER, "block: T.() -> Unit", priority = 80),
        CompletionItem("run", "run {  }", 6, CompletionKind.MEMBER, "block: T.() -> R", priority = 75),
        CompletionItem("takeIf", "takeIf {  }", 9, CompletionKind.MEMBER, "predicate: (T) -> Boolean", priority = 75),
        CompletionItem("takeUnless", "takeUnless {  }", 13, CompletionKind.MEMBER, "predicate: (T) -> Boolean", priority = 70),
        CompletionItem("toString", "toString()", 10, CompletionKind.MEMBER, "(): String", priority = 85),
        CompletionItem("equals", "equals()", 7, CompletionKind.MEMBER, "(other: Any?): Boolean", priority = 70),
        CompletionItem("hashCode", "hashCode()", 10, CompletionKind.MEMBER, "(): Int", priority = 70)
    )

    /**
     * Inspects text and cursor/selection to determine whether completion is permitted,
     * what prefix is being typed, and whether this is a member access following a dot.
     */
    fun analyzeContext(text: String, selection: TextRange): CompletionContext {
        val hasSelection = selection.min != selection.max
        val cursor = selection.end.coerceIn(0, text.length)

        val syntax = getSyntaxContext(text, cursor)
        if (syntax.isComment || syntax.isString || hasSelection) {
            return CompletionContext(
                prefix = "",
                prefixStart = cursor,
                cursor = cursor,
                isMember = false,
                receiver = null,
                isInsideComment = syntax.isComment,
                isInsideString = syntax.isString,
                hasSelection = hasSelection
            )
        }

        // Scan backwards from cursor to extract current identifier prefix
        var i = cursor - 1
        while (i >= 0 && (text[i].isLetterOrDigit() || text[i] == '_')) {
            i--
        }
        val prefixStart = i + 1
        val prefix = text.substring(prefixStart, cursor)

        // Check if preceded by a member access dot ('.', '?.', but not '..')
        var j = prefixStart - 1
        while (j >= 0 && text[j] == ' ') {
            j--
        }

        val isMember: Boolean
        val receiver: String?
        if (j >= 0 && text[j] == '.') {
            val beforeDot = if (j - 1 >= 0) text[j - 1] else null
            if (beforeDot == '.') {
                // Range operator '..'
                isMember = false
                receiver = null
            } else {
                isMember = true
                var k = if (beforeDot == '?') j - 2 else j - 1
                while (k >= 0 && text[k] == ' ') k--
                val receiverEnd = k + 1
                while (k >= 0 && (text[k].isLetterOrDigit() || text[k] == '_')) {
                    k--
                }
                val receiverStart = k + 1
                val searchBeforeReceiver = receiverStart - 1
                val statementStart = maxOf(
                    text.lastIndexOf('\n', searchBeforeReceiver),
                    text.lastIndexOf(';', searchBeforeReceiver)
                ) + 1
                val precedingToken = text.substring(statementStart, receiverStart).trimEnd().lastOrNull()
                receiver = if (
                    receiverStart < receiverEnd &&
                    (precedingToken == null || precedingToken !in setOf('.', '?', ')', ']', '}'))
                ) {
                    text.substring(receiverStart, receiverEnd)
                } else {
                    null
                }
            }
        } else {
            isMember = false
            receiver = null
        }

        return CompletionContext(
            prefix = prefix,
            prefixStart = prefixStart,
            cursor = cursor,
            isMember = isMember,
            receiver = receiver,
            isInsideComment = false,
            isInsideString = false,
            hasSelection = false
        )
    }

    /**
     * Extracts user-declared classes, functions, parameters, variables and properties from current source.
     * Comments and string contents are masked out so synthetic names inside strings or comments are ignored.
     */
    fun extractDeclarations(source: String): List<CompletionItem> {
        val clean = stripCommentsAndStrings(source)
        val items = mutableListOf<CompletionItem>()
        val seen = mutableSetOf<String>()

        fun addItem(item: CompletionItem) {
            val key = "${item.kind}:${item.label}"
            if (seen.add(key)) {
                items.add(item)
            }
        }

        // 1. Classes, data classes, interfaces, objects
        val classRegex = Regex("""\b(?:data\s+class|class|interface|object|enum\s+class)\s+([a-zA-Z_][a-zA-Z0-9_]*)(?:\s*\(([^)]*)\))?""")
        for (match in classRegex.findAll(clean)) {
            val name = match.groupValues[1]
            addItem(
                CompletionItem(
                    label = name,
                    kind = CompletionKind.TYPE,
                    detail = "class $name",
                    priority = 90
                )
            )

            // Extract constructor properties
            val constructorParams = match.groupValues.getOrNull(2)
            if (!constructorParams.isNullOrBlank()) {
                val propRegex = Regex("""\b(?:val|var)\s+([a-zA-Z_][a-zA-Z0-9_]*)(?:\s*:\s*([^=,]+))?""")
                for (propMatch in propRegex.findAll(constructorParams)) {
                    val propName = propMatch.groupValues[1]
                    val propType = propMatch.groupValues.getOrNull(2)?.trim()
                    addItem(
                        CompletionItem(
                            label = propName,
                            kind = CompletionKind.PROPERTY,
                            detail = propType ?: "$name property",
                            priority = 95
                        )
                    )
                }
            }
        }

        // 2. Functions and parameters
        val funRegex = Regex("""\bfun\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(([^)]*)\)(?:\s*:\s*([a-zA-Z0-9_<>?, ]+))?""")
        for (match in funRegex.findAll(clean)) {
            val funName = match.groupValues[1]
            val returnType = match.groupValues.getOrNull(3)?.trim()
            addItem(
                CompletionItem(
                    label = funName,
                    insertText = "$funName()",
                    cursorOffset = funName.length + 1,
                    kind = CompletionKind.FUNCTION,
                    detail = if (returnType != null) "(): $returnType" else "()",
                    priority = 95
                )
            )

            val paramsStr = match.groupValues[2]
            if (paramsStr.isNotBlank()) {
                val paramRegex = Regex("""(?:val\s+|var\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\s*:\s*([^=,]+)""")
                for (pMatch in paramRegex.findAll(paramsStr)) {
                    val pName = pMatch.groupValues[1]
                    val pType = pMatch.groupValues[2].trim()
                    addItem(
                        CompletionItem(
                            label = pName,
                            kind = CompletionKind.PARAMETER,
                            detail = pType,
                            priority = 100
                        )
                    )
                }
            }
        }

        // 3. Variables: val x = ..., var y: Int = ...
        val valVarRegex = Regex("""\b(?:val|var)\s+([a-zA-Z_][a-zA-Z0-9_]*)(?:\s*:\s*([^=,]+))?""")
        for (match in valVarRegex.findAll(clean)) {
            val varName = match.groupValues[1]
            val varType = match.groupValues.getOrNull(2)?.trim()
            addItem(
                CompletionItem(
                    label = varName,
                    kind = CompletionKind.VARIABLE,
                    detail = varType ?: "variable",
                    priority = 100
                )
            )
        }

        // 4. Destructuring: val (a, b) = ...
        val destructRegex = Regex("""\b(?:val|var)\s*\(\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*,\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\)""")
        for (match in destructRegex.findAll(clean)) {
            addItem(CompletionItem(label = match.groupValues[1], kind = CompletionKind.VARIABLE, detail = "destructured", priority = 100))
            addItem(CompletionItem(label = match.groupValues[2], kind = CompletionKind.VARIABLE, detail = "destructured", priority = 100))
        }

        // 5. For loops: for (item in items)
        val forRegex = Regex("""\bfor\s*\(\s*([a-zA-Z_][a-zA-Z0-9_]*)\s+in\b""")
        for (match in forRegex.findAll(clean)) {
            addItem(CompletionItem(label = match.groupValues[1], kind = CompletionKind.VARIABLE, detail = "loop variable", priority = 100))
        }

        // 6. Lambdas: { item -> ... }
        val lambdaRegex = Regex("""\{\s*([a-zA-Z_][a-zA-Z0-9_]*(?:\s*,\s*[a-zA-Z_][a-zA-Z0-9_]*)*)\s*->""")
        for (match in lambdaRegex.findAll(clean)) {
            val params = match.groupValues[1].split(",")
            for (p in params) {
                val name = p.trim()
                if (name.isNotEmpty()) {
                    addItem(CompletionItem(label = name, kind = CompletionKind.VARIABLE, detail = "lambda param", priority = 100))
                }
            }
        }

        return items
    }

    /**
     * Returns ordered suggestions matching the current cursor context.
     */
    fun getCompletions(
        text: String,
        selection: TextRange,
        explicit: Boolean = false
    ): List<CompletionItem> {
        val context = analyzeContext(text, selection)
        if (!context.canComplete) return emptyList()

        val prefix = context.prefix
        val isMember = context.isMember

        // If prefix is empty and not member completion and not explicitly triggered, return empty
        if (prefix.isEmpty() && !isMember && !explicit) {
            return emptyList()
        }

        val userItems = extractDeclarations(text)

        val candidatePool: List<CompletionItem> = if (isMember) {
            val receiverType = context.receiver?.let { inferReceiverType(text, context.cursor, it) }
            val userProps = receiverType?.customTypeName?.let { typeName ->
                extractPropertiesForClass(text, typeName)
            }.orEmpty()
            val applicableMembers = memberSuggestionsFor(receiverType?.kind ?: MemberReceiverKind.UNKNOWN)
            userProps + applicableMembers
        } else {
            userItems.filter { it.kind != CompletionKind.PROPERTY } +
                STANDARD_FUNCTIONS + STANDARD_TYPES + STANDARD_KEYWORDS
        }

        val filtered = if (prefix.isEmpty()) {
            candidatePool
        } else {
            candidatePool.filter { item ->
                item.label.startsWith(prefix, ignoreCase = true)
            }
        }

        val sorted = filtered.sortedWith(
            compareByDescending<CompletionItem> { it.label.startsWith(prefix, ignoreCase = false) }
                .thenByDescending { it.priority }
                .thenBy { it.label.length }
                .thenBy { it.label }
        )

        val seen = mutableSetOf<String>()
        val result = mutableListOf<CompletionItem>()
        for (item in sorted) {
            if (seen.add(item.label)) {
                result.add(item)
                if (result.size >= 25) break
            }
        }
        return result
    }

    private enum class MemberReceiverKind { STRING, LIST, SET, MAP, ARRAY, ITERABLE, SEQUENCE, UNKNOWN }

    private data class InferredReceiverType(
        val kind: MemberReceiverKind,
        val customTypeName: String? = null
    )

    private val universalMemberLabels = setOf(
        "let", "also", "apply", "run", "takeIf", "takeUnless", "toString", "equals", "hashCode"
    )
    private val stringMemberLabels = setOf(
        "length", "substring", "startsWith", "endsWith", "lowercase", "uppercase", "trim", "split",
        "replace", "matches", "toInt", "toIntOrNull", "toLong", "toLongOrNull", "toDouble",
        "toDoubleOrNull", "isNullOrEmpty", "isNullOrBlank"
    )
    private val collectionMemberLabels = setOf(
        "filter", "filterNot", "filterNotNull", "map", "mapNotNull", "flatMap", "forEach", "first",
        "firstOrNull", "last", "lastOrNull", "find", "any", "all", "none", "count", "sumOf", "sorted",
        "sortedBy", "sortedDescending", "reversed", "distinct", "distinctBy", "take", "drop", "joinToString",
        "contains", "get", "getOrNull", "size", "indices", "lastIndex", "isEmpty", "isNotEmpty", "associate",
        "associateBy", "groupBy", "chunked"
    )
    private val iterableMemberLabels = collectionMemberLabels - setOf("get", "getOrNull", "size", "indices", "lastIndex")
    private val sequenceMemberLabels = iterableMemberLabels - setOf("isEmpty", "isNotEmpty")
    private val mapMemberLabels = setOf(
        "filter", "filterNot", "map", "mapNotNull", "flatMap", "forEach", "containsKey", "containsValue",
        "get", "getOrDefault", "size", "isEmpty", "isNotEmpty", "count", "any", "all", "none"
    )

    private fun memberSuggestionsFor(kind: MemberReceiverKind): List<CompletionItem> {
        val labels = when (kind) {
            MemberReceiverKind.STRING -> stringMemberLabels
            MemberReceiverKind.LIST, MemberReceiverKind.ARRAY -> collectionMemberLabels
            MemberReceiverKind.SET -> iterableMemberLabels + "size"
            MemberReceiverKind.MAP -> mapMemberLabels
            MemberReceiverKind.ITERABLE -> iterableMemberLabels
            MemberReceiverKind.SEQUENCE -> sequenceMemberLabels
            MemberReceiverKind.UNKNOWN -> emptySet()
        }
        return MEMBER_SUGGESTIONS.filter { it.label in universalMemberLabels || it.label in labels }
    }

    /** Infers only obvious local declarations and parameter types; all other receivers stay unknown. */
    private fun inferReceiverType(source: String, cursor: Int, receiver: String): InferredReceiverType {
        val sourceBeforeCursor = source.substring(0, cursor.coerceIn(0, source.length))
        val clean = stripCommentsAndStrings(sourceBeforeCursor)
        val receiverName = Regex.escape(receiver)
        val declarationPattern = Regex(
            """\b(?:val|var)\s+$receiverName(?:\s*:\s*([A-Za-z_][A-Za-z0-9_?.<> ,]*))?\s*=\s*[^\n;]*"""
        )
        val declaration = declarationPattern.findAll(clean).lastOrNull()
        val explicitType = declaration?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        val inferredType = explicitType ?: declaration?.let { match ->
            val equals = clean.indexOf('=', match.range.first)
            val rawExpression = sourceBeforeCursor.substring(equals + 1)
                .substringBefore('\n')
                .substringBefore(';')
                .trim()
            when {
                rawExpression.startsWith('"') -> "String"
                rawExpression.startsWith('\'') -> "Char"
                Regex("""(?:listOf|mutableListOf)\s*\(""").containsMatchIn(rawExpression) -> "List"
                Regex("""(?:mapOf|mutableMapOf)\s*\(""").containsMatchIn(rawExpression) -> "Map"
                Regex("""(?:setOf|mutableSetOf)\s*\(""").containsMatchIn(rawExpression) -> "Set"
                Regex("""arrayOf\s*\(""").containsMatchIn(rawExpression) -> "Array"
                Regex("""(?:sequenceOf|generateSequence)\s*\(""").containsMatchIn(rawExpression) -> "Sequence"
                else -> Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
                    .find(rawExpression)?.groupValues?.get(1)
            }
        }
        val parameterType = Regex("""\b$receiverName\s*:\s*([A-Za-z_][A-Za-z0-9_?.<> ,]*)""")
            .findAll(clean)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val type = explicitType ?: inferredType ?: parameterType
        val baseType = type?.substringBefore('<')?.removeSuffix("?")?.substringAfterLast('.')
        return when (baseType) {
            "String", "CharSequence" -> InferredReceiverType(MemberReceiverKind.STRING)
            "List", "MutableList" -> InferredReceiverType(MemberReceiverKind.LIST)
            "Set", "MutableSet" -> InferredReceiverType(MemberReceiverKind.SET)
            "Iterable", "Collection", "MutableCollection" -> InferredReceiverType(MemberReceiverKind.ITERABLE)
            "Sequence" -> InferredReceiverType(MemberReceiverKind.SEQUENCE)
            "Map", "MutableMap" -> InferredReceiverType(MemberReceiverKind.MAP)
            "Array", "IntArray", "LongArray", "ShortArray", "ByteArray", "CharArray", "BooleanArray",
            "FloatArray", "DoubleArray" -> InferredReceiverType(MemberReceiverKind.ARRAY)
            null -> InferredReceiverType(MemberReceiverKind.UNKNOWN)
            else -> InferredReceiverType(MemberReceiverKind.UNKNOWN, customTypeName = baseType)
        }
    }

    private fun extractPropertiesForClass(source: String, className: String): List<CompletionItem> {
        val clean = stripCommentsAndStrings(source)
        val classPattern = Regex(
            """\b(?:data\s+class|class)\s+${Regex.escape(className)}(?:\s*<[^>]+>)?\s*\(([^)]*)\)"""
        )
        val constructorParameters = classPattern.find(clean)?.groupValues?.getOrNull(1).orEmpty()
        val propertyPattern = Regex("""\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*:\s*([^=,]+))?""")
        return propertyPattern.findAll(constructorParameters).map { match ->
            val propertyName = match.groupValues[1]
            val propertyType = match.groupValues.getOrNull(2)?.trim()
            CompletionItem(
                label = propertyName,
                kind = CompletionKind.PROPERTY,
                detail = propertyType ?: "$className property",
                priority = 95
            )
        }.toList()
    }

    /**
     * Applies a selected completion to [currentValue], replacing the active prefix
     * and placing the cursor at the designated snippet offset.
     */
    fun applyCompletion(
        currentValue: TextFieldValue,
        item: CompletionItem,
        context: CompletionContext? = null
    ): TextFieldValue {
        // Do not accept a completion against an IME pre-edit string or an old selection.
        // Re-analyzing here also makes a delayed/stale chip callback use the current cursor.
        if (currentValue.hasActiveComposition) return currentValue

        val currentText = currentValue.text
        val selection = currentValue.selection
        val ctx = analyzeContext(currentText, selection)
        if (!ctx.canComplete) return currentValue

        val matchesCurrentContext = when {
            ctx.prefix.isNotEmpty() -> item.label.startsWith(ctx.prefix, ignoreCase = true) &&
                (ctx.isMember == (item.kind == CompletionKind.MEMBER || item.kind == CompletionKind.PROPERTY))
            ctx.isMember -> item.kind == CompletionKind.MEMBER || item.kind == CompletionKind.PROPERTY
            // An empty unqualified prefix is offered only after explicit activation. The
            // supplied context must still match, so moving the cursor invalidates that chip.
            else -> context == ctx
        }
        if (!matchesCurrentContext) return currentValue

        val replaceStart = ctx.prefixStart
        val replaceEnd = ctx.cursor

        val before = currentText.substring(0, replaceStart)
        val after = currentText.substring(replaceEnd)
        val newText = before + item.insertText + after
        val newCursorPos = (replaceStart + item.cursorOffset).coerceIn(0, newText.length)

        return currentValue.copy(
            text = newText,
            selection = TextRange(newCursorPos),
            composition = null
        )
    }

    /**
     * Strips comment bodies and string literal contents, replacing them with spaces
     * while preserving exact character offsets and line structure.
     */
    fun stripCommentsAndStrings(source: String): String {
        val sb = StringBuilder(source.length)
        var inLineComment = false
        var inBlockComment = 0
        var inString = false
        var inTripleQuote = false
        var i = 0

        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else null
            val nextNext = if (i + 2 < source.length) source[i + 2] else null

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false
                    sb.append('\n')
                } else {
                    sb.append(' ')
                }
                i++
                continue
            }

            if (inBlockComment > 0) {
                if (c == '/' && next == '*') {
                    inBlockComment++
                    sb.append("  ")
                    i += 2
                    continue
                } else if (c == '*' && next == '/') {
                    inBlockComment--
                    sb.append("  ")
                    i += 2
                    continue
                }
                sb.append(if (c == '\n') '\n' else ' ')
                i++
                continue
            }

            if (inTripleQuote) {
                if (c == '"' && next == '"' && nextNext == '"') {
                    inTripleQuote = false
                    sb.append("\"\"\"")
                    i += 3
                    continue
                }
                sb.append(if (c == '\n') '\n' else ' ')
                i++
                continue
            }

            if (inString) {
                if (c == '\\') {
                    sb.append("  ")
                    i += 2
                    continue
                }
                if (c == '"') {
                    inString = false
                    sb.append('"')
                    i++
                    continue
                }
                sb.append(if (c == '\n') '\n' else ' ')
                i++
                continue
            }

            if (c == '/' && next == '/') {
                inLineComment = true
                sb.append("  ")
                i += 2
                continue
            }
            if (c == '/' && next == '*') {
                inBlockComment = 1
                sb.append("  ")
                i += 2
                continue
            }
            if (c == '"' && next == '"' && nextNext == '"') {
                inTripleQuote = true
                sb.append("\"\"\"")
                i += 3
                continue
            }
            if (c == '"') {
                inString = true
                sb.append('"')
                i++
                continue
            }

            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private data class SyntaxContext(
        val isComment: Boolean,
        val isString: Boolean
    )

    private fun getSyntaxContext(text: String, cursor: Int): SyntaxContext {
        var inLineComment = false
        var inBlockComment = 0
        var inString = false
        var inTripleQuote = false
        var inTemplateExpr = 0
        var i = 0
        val limit = cursor.coerceAtMost(text.length)

        while (i < limit) {
            val c = text[i]
            val next = if (i + 1 < text.length) text[i + 1] else null
            val nextNext = if (i + 2 < text.length) text[i + 2] else null

            if (inLineComment) {
                if (c == '\n') inLineComment = false
                i++
                continue
            }

            if (inBlockComment > 0) {
                if (c == '/' && next == '*') {
                    inBlockComment++
                    i += 2
                    continue
                } else if (c == '*' && next == '/') {
                    inBlockComment--
                    i += 2
                    continue
                }
                i++
                continue
            }

            if (inTripleQuote) {
                if (c == '$' && next == '{') {
                    inTemplateExpr++
                    i += 2
                    continue
                }
                if (inTemplateExpr > 0) {
                    if (c == '{') inTemplateExpr++
                    else if (c == '}') inTemplateExpr--
                    i++
                    continue
                }
                if (c == '"' && next == '"' && nextNext == '"') {
                    inTripleQuote = false
                    i += 3
                    continue
                }
                i++
                continue
            }

            if (inString) {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == '$' && next == '{') {
                    inTemplateExpr++
                    i += 2
                    continue
                }
                if (inTemplateExpr > 0) {
                    if (c == '{') inTemplateExpr++
                    else if (c == '}') inTemplateExpr--
                    i++
                    continue
                }
                if (c == '"') {
                    inString = false
                    i++
                    continue
                }
                i++
                continue
            }

            if (c == '/' && next == '/') {
                inLineComment = true
                i += 2
                continue
            }
            if (c == '/' && next == '*') {
                inBlockComment = 1
                i += 2
                continue
            }
            if (c == '"' && next == '"' && nextNext == '"') {
                inTripleQuote = true
                i += 3
                continue
            }
            if (c == '"') {
                inString = true
                i++
                continue
            }
            i++
        }

        val isComment = inLineComment || inBlockComment > 0
        val isString = (inString || inTripleQuote) && inTemplateExpr == 0
        return SyntaxContext(isComment = isComment, isString = isString)
    }
}

/**
 * Scrollable row displaying available code completion suggestions.
 */
@Composable
fun CodeCompletionBar(
    suggestions: List<CompletionItem>,
    onSelectSuggestion: (CompletionItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val extendedColors = MaterialTheme.extendedColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            suggestions.forEach { item ->
                CompletionChip(
                    item = item,
                    onClick = { onSelectSuggestion(item) },
                    extendedColors = extendedColors
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(32.dp)
                .semantics { contentDescription = "Dismiss completions" }
        ) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CompletionChip(
    item: CompletionItem,
    onClick: () -> Unit,
    extendedColors: ExtendedColors,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (item.kind) {
        CompletionKind.FUNCTION -> MaterialTheme.colorScheme.primary
        CompletionKind.VARIABLE, CompletionKind.PARAMETER, CompletionKind.PROPERTY -> MaterialTheme.colorScheme.secondary
        CompletionKind.TYPE -> MaterialTheme.colorScheme.tertiary
        CompletionKind.KEYWORD -> extendedColors.codeKeyword
        CompletionKind.MEMBER -> extendedColors.codeString
        CompletionKind.SNIPPET -> extendedColors.codeNumber
    }

    val badgeText = when (item.kind) {
        CompletionKind.FUNCTION -> "fn"
        CompletionKind.VARIABLE -> "val"
        CompletionKind.PARAMETER -> "param"
        CompletionKind.PROPERTY -> "prop"
        CompletionKind.TYPE -> "type"
        CompletionKind.KEYWORD -> "kw"
        CompletionKind.MEMBER -> "mem"
        CompletionKind.SNIPPET -> "snip"
    }

    AssistChip(
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = badgeColor
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!item.detail.isNullOrBlank()) {
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .semantics { contentDescription = "Suggestion: ${item.label}" }
    )
}
