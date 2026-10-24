package dev.agam.learn.domain

import dev.agam.learn.model.Exercise

/**
 * Calculates XP rewards for completing lessons, exercises, and multi-part problem parts.
 */
interface XpPolicy {
    /**
     * Determines XP awarded for completing an exercise.
     *
     * @param exercise The exercise attempted.
     * @param isFirstAttempt True if passed on the initial attempt, false on retry.
     * @return Experience points awarded.
     */
    fun xpForExercise(exercise: Exercise, isFirstAttempt: Boolean): Int
}
