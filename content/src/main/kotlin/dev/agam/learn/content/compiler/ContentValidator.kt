package dev.agam.learn.content.compiler

import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise

class ContentValidationException(message: String) : IllegalArgumentException(message)

object ContentValidator {

    private val CLOZE_PLACEHOLDER_REGEX = Regex("____\\d+")

    /**
     * Validates a decoded [Course] against the rules in docs/CONTENT_SCHEMA.md:
     * - Rule 1: Every id is unique across the whole course.
     * - Rule 2: correct_index is within options bounds.
     * - Rule 3: Cloze placeholders in blanks appear exactly once in template, and every ____N in template has a blank.
     * - Rule 6: Markdown fields are non-empty.
     */
    fun validate(course: Course) {
        validateUniqueIds(course)
        validateRules(course)
    }

    /**
     * Rule 4 & 5 verification stub. Requires kotlin-compiler-server.
     */
    fun verifyWithCompilerServer(course: Course) {
        // Rule 4: predict_output programs compile and print exactly expected_output (checked in CI).
        // Rule 5: Every reference_solution passes its hidden_tests (checked in CI).
        // TODO: Connect to kotlin-compiler-server in CI verification pipeline.
    }

    private fun validateUniqueIds(course: Course) {
        val seenIds = mutableMapOf<String, String>()

        fun record(id: String, type: String) {
            if (id.isBlank()) {
                throw ContentValidationException("Found empty id for $type")
            }
            val existing = seenIds.put(id, type)
            if (existing != null) {
                throw ContentValidationException(
                    "Rule 1 violation: Duplicate id '$id' found in $type (already declared as $existing)"
                )
            }
        }

        record(course.id, "course")
        for (chapter in course.chapters) {
            record(chapter.id, "chapter")
            for (lesson in chapter.lessons) {
                record(lesson.id, "lesson")
                for (exercise in lesson.exercises) {
                    record(exercise.id, "exercise")
                    if (exercise is Exercise.MultiPartProblem) {
                        for (part in exercise.parts) {
                            record(part.id, "problem_part")
                        }
                    }
                }
            }
        }
    }

    private fun validateRules(course: Course) {
        for (chapter in course.chapters) {
            if (chapter.summary.isBlank()) {
                throw ContentValidationException("Rule 6 violation: Chapter '${chapter.id}' summary cannot be empty")
            }

            for (lesson in chapter.lessons) {
                if (lesson.explanation.isBlank()) {
                    throw ContentValidationException("Rule 6 violation: Lesson '${lesson.id}' explanation cannot be empty")
                }

                for (exercise in lesson.exercises) {
                    if (exercise.prompt.isBlank()) {
                        throw ContentValidationException("Rule 6 violation: Exercise '${exercise.id}' prompt cannot be empty")
                    }

                    when (exercise) {
                        is Exercise.MultipleChoice -> {
                            // Rule 2
                            if (exercise.options.size < 2 || exercise.options.size > 5) {
                                throw ContentValidationException(
                                    "MultipleChoice '${exercise.id}' options count must be between 2 and 5, was ${exercise.options.size}"
                                )
                            }
                            if (exercise.correctIndex !in exercise.options.indices) {
                                throw ContentValidationException(
                                    "Rule 2 violation: MultipleChoice '${exercise.id}' correct_index ${exercise.correctIndex} out of bounds (options size: ${exercise.options.size})"
                                )
                            }
                            if (exercise.explanation.isBlank()) {
                                throw ContentValidationException("Rule 6 violation: MultipleChoice '${exercise.id}' explanation cannot be empty")
                            }
                        }

                        is Exercise.Cloze -> {
                            // Rule 3
                            if (exercise.blanks.isEmpty()) {
                                throw ContentValidationException("Cloze '${exercise.id}' must declare at least one blank")
                            }
                            val placeholdersInTemplate = CLOZE_PLACEHOLDER_REGEX.findAll(exercise.template)
                                .map { it.value }
                                .toList()

                            val blankPlaceholders = exercise.blanks.map { it.placeholder }

                            for (placeholder in blankPlaceholders) {
                                val occurrences = placeholdersInTemplate.count { it == placeholder }
                                if (occurrences != 1) {
                                    throw ContentValidationException(
                                        "Rule 3 violation: Cloze '${exercise.id}' placeholder '$placeholder' appears $occurrences times in template (must appear exactly once)"
                                    )
                                }
                            }

                            for (placeholder in placeholdersInTemplate) {
                                if (placeholder !in blankPlaceholders) {
                                    throw ContentValidationException(
                                        "Rule 3 violation: Cloze '${exercise.id}' template contains '$placeholder' with no matching blank definition"
                                    )
                                }
                            }

                            if (exercise.explanation.isBlank()) {
                                throw ContentValidationException("Rule 6 violation: Cloze '${exercise.id}' explanation cannot be empty")
                            }
                        }

                        is Exercise.PredictOutput -> {
                            if (exercise.code.isBlank()) {
                                throw ContentValidationException("PredictOutput '${exercise.id}' code cannot be empty")
                            }
                            if (exercise.expectedOutput.isEmpty()) {
                                throw ContentValidationException("PredictOutput '${exercise.id}' expected_output cannot be empty")
                            }
                            if (exercise.explanation.isBlank()) {
                                throw ContentValidationException("Rule 6 violation: PredictOutput '${exercise.id}' explanation cannot be empty")
                            }
                        }

                        is Exercise.WriteFunction -> {
                            if (exercise.starterCode.isBlank()) {
                                throw ContentValidationException("WriteFunction '${exercise.id}' starter_code cannot be empty")
                            }
                            if (exercise.hiddenTests.isBlank()) {
                                throw ContentValidationException("WriteFunction '${exercise.id}' hidden_tests cannot be empty")
                            }
                            if (exercise.referenceSolution.isBlank()) {
                                throw ContentValidationException("WriteFunction '${exercise.id}' reference_solution cannot be empty")
                            }
                            exercise.explanation?.let {
                                if (it.isBlank()) {
                                    throw ContentValidationException("Rule 6 violation: WriteFunction '${exercise.id}' explanation cannot be blank when present")
                                }
                            }
                        }

                        is Exercise.MultiPartProblem -> {
                            if (exercise.story.isBlank()) {
                                throw ContentValidationException("Rule 6 violation: MultiPartProblem '${exercise.id}' story cannot be empty")
                            }
                            if (exercise.parts.isEmpty()) {
                                throw ContentValidationException("MultiPartProblem '${exercise.id}' parts cannot be empty")
                            }
                            for (part in exercise.parts) {
                                if (part.prompt.isBlank()) {
                                    throw ContentValidationException("Rule 6 violation: ProblemPart '${part.id}' prompt cannot be empty")
                                }
                                if (part.hiddenTests.isBlank()) {
                                    throw ContentValidationException("ProblemPart '${part.id}' hidden_tests cannot be empty")
                                }
                                if (part.referenceSolution.isBlank()) {
                                    throw ContentValidationException("ProblemPart '${part.id}' reference_solution cannot be empty")
                                }
                                if (part.expected.isBlank()) {
                                    throw ContentValidationException("Rule 6 violation: ProblemPart '${part.id}' expected cannot be empty")
                                }
                            }
                            exercise.discussion?.let {
                                if (it.isBlank()) {
                                    throw ContentValidationException("Rule 6 violation: MultiPartProblem '${exercise.id}' discussion cannot be blank when present")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
