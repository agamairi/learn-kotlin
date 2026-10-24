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

    /** XP for one part of a multi-part problem; zero unless the part was passed. */
    fun xpForMultiPartPart(correct: Boolean): Int

    /** XP cost for an AI hint level (1 = nudge, 2 = approach, 3 = near-solution). */
    fun hintCost(level: Int): Int = when (level) {
        1 -> DefaultXpPolicy.HINT_LEVEL_ONE_COST
        2 -> DefaultXpPolicy.HINT_LEVEL_TWO_COST
        3 -> DefaultXpPolicy.HINT_LEVEL_THREE_COST
        else -> error("Hint level must be between 1 and 3")
    }

    /** Deducts a hint cost without allowing the exercise's XP balance below zero. */
    fun xpAfterHint(currentExerciseXp: Int, level: Int): Int =
        maxOf(0, currentExerciseXp - hintCost(level))
}

/**
 * The default XP economy for verified course exercises.
 *
 * A caller must pass [correct] when it has the result of an attempt; the
 * two-argument interface method remains a compatibility shorthand for a
 * completed (correct) exercise.
 */
class DefaultXpPolicy : XpPolicy {

    override fun xpForExercise(exercise: Exercise, isFirstAttempt: Boolean): Int =
        xpForExercise(exercise, isFirstAttempt, correct = true)

    /** Returns the XP for an attempt, awarding zero for an incorrect answer. */
    fun xpForExercise(
        exercise: Exercise,
        isFirstAttempt: Boolean,
        correct: Boolean
    ): Int {
        if (!correct) return 0
        return when (exercise) {
            is Exercise.MultipleChoice,
            is Exercise.Cloze,
            is Exercise.PredictOutput -> CARD_XP
            is Exercise.WriteFunction -> if (isFirstAttempt) CODE_FIRST_TRY_XP else CODE_RETRY_XP
            is Exercise.MultiPartProblem -> MULTI_PART_XP
        }
    }

    /** XP awarded for one successfully completed part of a multi-part problem. */
    override fun xpForMultiPartPart(correct: Boolean): Int = if (correct) MULTI_PART_XP else 0

    /** XP cost for an AI hint level (1 = nudge, 2 = approach, 3 = near-solution). */
    override fun hintCost(level: Int): Int = when (level) {
        1 -> HINT_LEVEL_ONE_COST
        2 -> HINT_LEVEL_TWO_COST
        3 -> HINT_LEVEL_THREE_COST
        else -> error("Hint level must be between 1 and 3")
    }

    /** Deducts a hint cost without allowing the exercise's XP balance below zero. */
    override fun xpAfterHint(currentExerciseXp: Int, level: Int): Int =
        maxOf(0, currentExerciseXp - hintCost(level))

    companion object {
        const val CARD_XP: Int = 10
        const val CODE_FIRST_TRY_XP: Int = 25
        const val CODE_RETRY_XP: Int = 15
        const val MULTI_PART_XP: Int = 50
        const val HINT_LEVEL_ONE_COST: Int = 2
        const val HINT_LEVEL_TWO_COST: Int = 5
        const val HINT_LEVEL_THREE_COST: Int = 10
    }
}

/** Compatibility name for callers that use the conventional implementation suffix. */
typealias XpPolicyImpl = DefaultXpPolicy
