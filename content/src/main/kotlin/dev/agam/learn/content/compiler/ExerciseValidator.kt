package dev.agam.learn.content.compiler

import dev.agam.learn.model.Blank
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ProblemPart

/**
 * Checks the fields of one exercise against docs/CONTENT_SCHEMA.md. Ids are checked by [ContentValidator], which sees
 * the whole course; this object checks everything that depends only on the exercise itself.
 */
internal object ExerciseValidator {

    private const val MIN_OPTIONS = 2
    private const val MAX_OPTIONS = 5
    private const val EXACT_BLANK_OPTIONS = 4
    private const val MIN_CODE_PUZZLE_BLANKS = 3
    private const val MAX_CODE_PUZZLE_BLANKS = 5

    private val PLACEHOLDER = Regex("____\\d+")
    private val UNNUMBERED_BLANK = Regex("____(?!\\d)")
    private val MAIN_FUNCTION = Regex("\\bfun\\s+main\\s*\\(")

    /** Checks [exercise], whose JSON path inside the chapter file is [path]. */
    fun validate(site: ContentSite, path: String, exercise: Exercise) {
        site.requireText("$path.prompt", exercise.prompt, "prompt")
        when (exercise) {
            is Exercise.MultipleChoice -> validateMultipleChoice(site, path, exercise)
            is Exercise.Cloze -> validateCloze(site, path, exercise)
            is Exercise.PredictOutput -> validatePredictOutput(site, path, exercise)
            is Exercise.WriteFunction -> validateWriteFunction(site, path, exercise)
            is Exercise.MultiPartProblem -> validateMultiPartProblem(site, path, exercise)
        }
    }

    private fun validateMultipleChoice(site: ContentSite, path: String, exercise: Exercise.MultipleChoice) {
        val count = exercise.options.size
        if (count !in MIN_OPTIONS..MAX_OPTIONS) {
            site.reportError("$path.options", "has $count options; use $MIN_OPTIONS to $MAX_OPTIONS")
        }
        exercise.options.forEachIndexed { index, option -> site.requireText("$path.options[$index]", option, "option") }
        duplicates(exercise.options).forEach { option ->
            site.reportError("$path.options", "option '$option' appears more than once")
        }
        if (exercise.correctIndex !in exercise.options.indices) {
            site.reportError(
                "$path.correct_index",
                "correct_index ${exercise.correctIndex} is out of range for $count options"
            )
        }
        site.requireOptionalText("$path.code", exercise.code, "code")
        site.requireText("$path.explanation", exercise.explanation, "explanation")
    }

    private fun validateCloze(site: ContentSite, path: String, exercise: Exercise.Cloze) {
        site.requireText("$path.template", exercise.template, "template")
        if (UNNUMBERED_BLANK.containsMatchIn(exercise.template)) {
            site.reportError("$path.template", "has a blank written as ____ without a number; use ____1, ____2, ...")
        }
        if (exercise.blanks.isEmpty()) {
            site.reportError("$path.blanks", "must declare at least one blank")
        }
        val declared = exercise.blanks.map { it.placeholder }
        duplicates(declared).forEach { placeholder ->
            site.reportError("$path.blanks", "placeholder '$placeholder' is declared more than once")
        }

        val inTemplate = PLACEHOLDER.findAll(exercise.template).map { it.value }.toList()
        exercise.blanks.forEachIndexed { index, blank ->
            val blankPath = "$path.blanks[$index]"
            validateBlank(site, blankPath, blank, inTemplate, requireOptions = !exercise.aiGenerated)
        }
        inTemplate.distinct().filter { it !in declared }.forEach { placeholder ->
            site.reportError("$path.template", "template contains '$placeholder' with no matching blank")
        }
        site.requireText("$path.explanation", exercise.explanation, "explanation")
    }

    private fun validateBlank(
        site: ContentSite,
        blankPath: String,
        blank: Blank,
        inTemplate: List<String>,
        requireOptions: Boolean
    ) {
        if (!PLACEHOLDER.matches(blank.placeholder)) {
            site.reportError("$blankPath.placeholder", "placeholder '${blank.placeholder}' must look like ____1")
        }
        val occurrences = inTemplate.count { it == blank.placeholder }
        if (occurrences != 1) {
            site.reportError(
                "$blankPath.placeholder",
                "placeholder '${blank.placeholder}' appears $occurrences times in template; it must appear exactly once"
            )
        }
        if (blank.accepted.isEmpty()) {
            site.reportError("$blankPath.accepted", "must list at least one accepted answer")
        }
        blank.accepted.forEachIndexed { answerIndex, answer ->
            site.requireText("$blankPath.accepted[$answerIndex]", answer, "accepted answer")
        }

        if (requireOptions || blank.options.isNotEmpty()) {
            if (blank.options.size != EXACT_BLANK_OPTIONS) {
                site.reportError("$blankPath.options", "must declare exactly $EXACT_BLANK_OPTIONS options, found ${blank.options.size}")
            }
            blank.options.forEachIndexed { optionIndex, option ->
                site.requireText("$blankPath.options[$optionIndex]", option, "option")
            }
            val distinctOptions = blank.options.map { it.trim() }.distinct()
            if (distinctOptions.size != blank.options.size) {
                site.reportError("$blankPath.options", "options must be distinct")
            }
            val matchingCount = blank.options.count { option ->
                blank.accepted.any { accepted ->
                    if (blank.caseSensitive) option.trim() == accepted.trim()
                    else option.trim().equals(accepted.trim(), ignoreCase = true)
                }
            }
            if (matchingCount != 1) {
                site.reportError(
                    "$blankPath.options",
                    "options must contain exactly one accepted equivalent, found $matchingCount"
                )
            }
        }
    }

    private fun validateCodePuzzle(site: ContentSite, path: String, puzzle: CodePuzzle, referenceSolution: String) {
        site.requireText("$path.template", puzzle.template, "template")
        if (UNNUMBERED_BLANK.containsMatchIn(puzzle.template)) {
            site.reportError("$path.template", "has a blank written as ____ without a number; use ____1, ____2, ...")
        }
        if (puzzle.blanks.size !in MIN_CODE_PUZZLE_BLANKS..MAX_CODE_PUZZLE_BLANKS) {
            site.reportError(
                "$path.blanks",
                "must declare $MIN_CODE_PUZZLE_BLANKS to $MAX_CODE_PUZZLE_BLANKS blanks, found ${puzzle.blanks.size}"
            )
        }
        val declared = puzzle.blanks.map { it.placeholder }
        duplicates(declared).forEach { placeholder ->
            site.reportError("$path.blanks", "placeholder '$placeholder' is declared more than once")
        }

        val inTemplate = PLACEHOLDER.findAll(puzzle.template).map { it.value }.toList()
        puzzle.blanks.forEachIndexed { index, blank ->
            val blankPath = "$path.blanks[$index]"
            validateBlank(site, blankPath, blank, inTemplate, requireOptions = true)
        }
        inTemplate.distinct().filter { it !in declared }.forEach { placeholder ->
            site.reportError("$path.template", "template contains '$placeholder' with no matching blank")
        }
        site.requireOptionalText("$path.explanation", puzzle.explanation, "explanation")

        val acceptedOptions = puzzle.blanks.map { blank ->
            blank.options.singleOrNull { option ->
                blank.accepted.any { accepted ->
                    if (blank.caseSensitive) option.trim() == accepted.trim()
                    else option.trim().equals(accepted.trim(), ignoreCase = true)
                }
            }
        }
        if (acceptedOptions.all { it != null } && referenceSolution.isNotBlank()) {
            val reconstructed = puzzle.blanks.zip(acceptedOptions).fold(puzzle.template) { code, (blank, option) ->
                code.replace(blank.placeholder, option!!)
            }
            if (reconstructed.replace("\r\n", "\n") != referenceSolution.replace("\r\n", "\n")) {
                site.reportError(
                    "$path.template",
                    "filling the accepted choices does not reconstruct reference_solution exactly"
                )
            }
        }
    }

    private fun validatePredictOutput(site: ContentSite, path: String, exercise: Exercise.PredictOutput) {
        site.requireText("$path.code", exercise.code, "code")
        if (exercise.code.isNotBlank() && !MAIN_FUNCTION.containsMatchIn(exercise.code)) {
            site.reportError("$path.code", "must be a complete program with fun main()")
        }
        if (exercise.expectedOutput.isEmpty()) {
            site.reportError("$path.expected_output", "expected_output cannot be empty")
        }
        site.requireText("$path.explanation", exercise.explanation, "explanation")
    }

    private fun validateWriteFunction(site: ContentSite, path: String, exercise: Exercise.WriteFunction) {
        site.requireText("$path.starter_code", exercise.starterCode, "starter_code")
        requireHiddenTests(site, "$path.hidden_tests", exercise.hiddenTests)
        site.requireText("$path.reference_solution", exercise.referenceSolution, "reference_solution")
        site.requireHints("$path.hints", exercise.hints)
        site.requireOptionalText("$path.explanation", exercise.explanation, "explanation")
        val guided = exercise.guided
        if (guided == null) {
            site.reportError("$path.guided", "must provide guided code puzzle data")
        } else {
            validateCodePuzzle(site, "$path.guided", guided, exercise.referenceSolution)
        }
    }

    private fun validateMultiPartProblem(site: ContentSite, path: String, exercise: Exercise.MultiPartProblem) {
        site.requireText("$path.story", exercise.story, "story")
        exercise.parts.forEachIndexed { index, part -> validatePart(site, "$path.parts[$index]", part) }
        exercise.faq.forEachIndexed { index, item ->
            site.requireText("$path.faq[$index].question", item.question, "question")
            site.requireText("$path.faq[$index].answer", item.answer, "answer")
        }
        site.requireOptionalText("$path.discussion", exercise.discussion, "discussion")
        site.requireOptionalText("$path.source_note", exercise.sourceNote, "source_note")
    }

    private fun validatePart(site: ContentSite, path: String, part: ProblemPart) {
        site.requireText("$path.prompt", part.prompt, "prompt")
        site.requireOptionalText("$path.starter_code", part.starterCode, "starter_code")
        requireHiddenTests(site, "$path.hidden_tests", part.hiddenTests)
        site.requireText("$path.reference_solution", part.referenceSolution, "reference_solution")
        site.requireText("$path.expected", part.expected, "expected")
        site.requireHints("$path.hints", part.hints)
        val guided = part.guided
        if (guided == null) {
            site.reportError("$path.guided", "must provide guided code puzzle data")
        } else {
            validateCodePuzzle(site, "$path.guided", guided, part.referenceSolution)
        }
    }

    /** Hidden tests are JUnit 4 classes; the schema forbids kotlin.test. */
    private fun requireHiddenTests(site: ContentSite, path: String, tests: String) {
        site.requireText(path, tests, "hidden_tests")
        if (tests.contains("kotlin.test")) {
            site.reportError(path, "must use JUnit 4 (org.junit), not kotlin.test")
        }
    }

    private fun duplicates(values: List<String>): Set<String> =
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
