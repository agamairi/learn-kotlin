package dev.agam.learn.domain

import dev.agam.learn.model.Lesson

/**
 * Whether a lesson counts as complete. A lesson is complete once its completion was saved when the
 * learner finished it, or when every one of its exercises has a passing result.
 */
fun isLessonComplete(
    lesson: Lesson,
    savedCompletions: Set<String>,
    passedExerciseIds: Set<String>
): Boolean =
    lesson.id in savedCompletions ||
        (lesson.exercises.isNotEmpty() && lesson.exercises.all { it.id in passedExerciseIds })
