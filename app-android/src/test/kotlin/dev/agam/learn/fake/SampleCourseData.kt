package dev.agam.learn.fake

import dev.agam.learn.model.Blank
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.FaqItem
import dev.agam.learn.model.Lesson
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.runner.LanguageId

/**
 * Embedded sample curriculum for offline development, previews, and tests.
 */
object SampleCourseData {

    val defaultCourse: Course by lazy {
        Course(
            id = "kotlin-foundations",
            language = LanguageId.KOTLIN,
            title = "Kotlin Foundations",
            description = "Master Kotlin from syntax essentials to idioms and interview problem solving.",
            chapters = listOf(
                Chapter(
                    id = "ch01-intro",
                    title = "Introduction to Kotlin",
                    summary = "Variables, basic types, functions, and string templates.",
                    lessons = listOf(
                        Lesson(
                            id = "lesson-01-syntax",
                            title = "Kotlin Syntax Essentials",
                            explanation = """
                                Kotlin is a modern, statically typed language that runs on the JVM, Android, and other platforms.
                                Variables are declared using `val` (read-only reference) or `var` (mutable variable).
                                Always prefer `val` for immutability and thread safety.
                            """.trimIndent(),
                            exercises = listOf(
                                Exercise.MultipleChoice(
                                    id = "mc-val-immutability",
                                    prompt = "Which keyword declares a read-only local variable in Kotlin?",
                                    code = "val message = \"Hello\"\n// message = \"World\" // Compile error!\n",
                                    options = listOf("val", "var", "let", "const"),
                                    correctIndex = 0,
                                    explanation = "In Kotlin, `val` is used to declare read-only properties and variables."
                                ),
                                Exercise.Cloze(
                                    id = "cloze-string-template",
                                    prompt = "Complete the string template to print the name.",
                                    template = "println(\"Hello, ____1!\")",
                                    blanks = listOf(
                                        Blank(
                                            placeholder = "____1",
                                            accepted = listOf("\$name"),
                                            caseSensitive = true,
                                            options = listOf("\$name", "name", "@name", "#name")
                                        )
                                    ),
                                    explanation = "Kotlin string templates use the `$` symbol to interpolate variables."
                                ),
                                Exercise.PredictOutput(
                                    id = "po-string-interpolation",
                                    prompt = "What will this program print to stdout?",
                                    code = "fun main() {\n    val a = 5\n    val b = 10\n    println(\"Sum is \${a + b}\")\n}\n",
                                    expectedOutput = "Sum is 15",
                                    explanation = "Expressions inside string templates are enclosed with `\${}`."
                                ),
                                Exercise.WriteFunction(
                                    id = "wf-greet",
                                    prompt = "Write a function `greet(name: String): String` that returns `\"Hello, <name>!\"`.",
                                    starterCode = "fun greet(name: String): String = TODO()\n",
                                    hiddenTests = "import org.junit.Test\nimport org.junit.Assert.assertEquals\n\nclass GreetTest {\n    @Test\n    fun testGreet() {\n        assertEquals(\"Hello, Agam!\", greet(\"Agam\"))\n    }\n}\n",
                                    referenceSolution = "fun greet(name: String): String = \"Hello, \$name!\"\n",
                                    hints = listOf("Use string interpolation with \$name"),
                                    explanation = "Expression body syntax makes one-line functions concise.",
                                    guided = CodePuzzle(
                                        template = "fun greet(name: String): String = ____1\n",
                                        blanks = listOf(
                                            Blank(
                                                placeholder = "____1",
                                                accepted = listOf("\"Hello, \$name!\""),
                                                options = listOf(
                                                    "\"Hello, \$name!\"",
                                                    "\"Hello, name!\"",
                                                    "\"Hello, @name!\"",
                                                    "\"Hello!\""
                                                )
                                            )
                                        ),
                                        explanation = "Expression body with string template."
                                    )
                                ),
                                Exercise.MultiPartProblem(
                                    id = "mp-ledger-intro",
                                    prompt = "Build an in-memory payment ledger.",
                                    story = "You are building a lightweight in-memory transaction balance tracker.",
                                    parts = listOf(
                                        ProblemPart(
                                            id = "mp-ledger-part-1",
                                            prompt = "Implement `calculateBalance(credits: List<Long>, debits: List<Long>): Long`.",
                                            starterCode = "fun calculateBalance(credits: List<Long>, debits: List<Long>): Long = TODO()\n",
                                            hiddenTests = "import org.junit.Test\nimport org.junit.Assert.assertEquals\n\nclass BalanceTest {\n    @Test\n    fun testBalance() {\n        assertEquals(70L, calculateBalance(listOf(100L), listOf(30L)))\n    }\n}\n",
                                            referenceSolution = "fun calculateBalance(credits: List<Long>, debits: List<Long>): Long = credits.sum() - debits.sum()\n",
                                            expected = "Returns credits sum minus debits sum.",
                                            guided = CodePuzzle(
                                                template = "fun calculateBalance(credits: List<Long>, debits: List<Long>): Long = ____1\n",
                                                blanks = listOf(
                                                    Blank(
                                                        placeholder = "____1",
                                                        accepted = listOf("credits.sum() - debits.sum()"),
                                                        options = listOf(
                                                            "credits.sum() - debits.sum()",
                                                            "credits.sum() + debits.sum()",
                                                            "debits.sum() - credits.sum()",
                                                            "credits.sum()"
                                                        )
                                                    )
                                                ),
                                                explanation = "Subtract debits sum from credits sum."
                                            )
                                        )
                                    ),
                                    faq = listOf(
                                        FaqItem(
                                            question = "Can lists be empty?",
                                            answer = "Yes, empty lists evaluate to zero sum."
                                        )
                                    ),
                                    discussion = "In later parts, transaction IDs and fee calculations will be added.",
                                    sourceNote = "Stripe terminal drill."
                                )
                            )
                        ),
                        Lesson(
                            id = "lesson-02-functions",
                            title = "Functions and Default Arguments",
                            explanation = """
                                Functions in Kotlin are declared with `fun`. Parameters can have default values, reducing the need for method overloading.
                                Single-expression functions can omit the curly braces and return type declaration.
                            """.trimIndent(),
                            exercises = listOf(
                                Exercise.MultipleChoice(
                                    id = "mc-default-args",
                                    prompt = "How do you specify a default parameter value in Kotlin?",
                                    code = "fun greet(name: String = \"Guest\"): String",
                                    options = listOf(
                                        "Using `= defaultValue` after the parameter type",
                                        "Using `@Default` annotation",
                                        "Overloading with multiple method definitions",
                                        "Using `?:` Elvis operator"
                                    ),
                                    correctIndex = 0,
                                    explanation = "Default arguments are defined right in the parameter list with `=`."
                                ),
                                Exercise.Cloze(
                                    id = "cloze-single-expression",
                                    prompt = "Convert this to a single-expression function.",
                                    template = "fun square(x: Int): Int ____1 x * x",
                                    blanks = listOf(
                                        Blank(
                                            placeholder = "____1",
                                            accepted = listOf("="),
                                            caseSensitive = true
                                        )
                                    ),
                                    explanation = "Single-expression functions use `=` followed directly by the expression."
                                ),
                                Exercise.PredictOutput(
                                    id = "po-default-greet",
                                    prompt = "What does this program print?",
                                    code = "fun greet(user: String = \"Friend\") = println(\"Hi, \$user\")\nfun main() {\n    greet()\n}\n",
                                    expectedOutput = "Hi, Friend",
                                    explanation = "Calling `greet()` without arguments uses the default value `\"Friend\"`."
                                )
                            )
                        )
                    )
                ),
                Chapter(
                    id = "ch02-collections",
                    title = "The Collections Toolkit",
                    summary = "Lists, sets, maps, grouping, filtering, and functional operations.",
                    lessons = listOf(
                        Lesson(
                            id = "lesson-03-filtering",
                            title = "Filtering and Mapping",
                            explanation = """
                                Kotlin collections have rich standard library extensions like `filter`, `map`, `groupBy`, and `associate`.
                                `filter` returns elements matching a predicate, while `map` transforms each element.
                            """.trimIndent(),
                            exercises = listOf(
                                Exercise.MultipleChoice(
                                    id = "mc-filter-map",
                                    prompt = "Which function transforms elements from a list into a new list of results?",
                                    code = "val lengths = listOf(\"apple\", \"banana\").map { it.length }",
                                    options = listOf("map", "filter", "forEach", "reduce"),
                                    correctIndex = 0,
                                    explanation = "`map` applies a transformation lambda to each element."
                                ),
                                Exercise.Cloze(
                                    id = "cloze-filter-even",
                                    prompt = "Filter the list to only include even numbers.",
                                    template = "val evens = numbers.____1 { it % 2 == 0 }",
                                    blanks = listOf(
                                        Blank(
                                            placeholder = "____1",
                                            accepted = listOf("filter"),
                                            caseSensitive = true
                                        )
                                    ),
                                    explanation = "`filter` retains only elements that satisfy the predicate."
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}
