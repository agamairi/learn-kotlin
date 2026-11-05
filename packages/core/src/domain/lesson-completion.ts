import type { Lesson } from '../models/lesson.ts';

/**
 * Evaluates whether a lesson counts as complete.
 * A lesson is complete once its completion was saved when the learner finished it,
 * or when every one of its exercises has a passing result.
 */
export function isLessonComplete(
  lesson: Lesson,
  savedCompletions: Set<string> | Iterable<string>,
  passedExerciseIds: Set<string> | Iterable<string>
): boolean {
  const completionsSet = savedCompletions instanceof Set ? savedCompletions : new Set(savedCompletions);
  if (completionsSet.has(lesson.id)) {
    return true;
  }

  if (!lesson.exercises || lesson.exercises.length === 0) {
    return false;
  }

  const passedSet = passedExerciseIds instanceof Set ? passedExerciseIds : new Set(passedExerciseIds);
  return lesson.exercises.every((exercise) => passedSet.has(exercise.id));
}
