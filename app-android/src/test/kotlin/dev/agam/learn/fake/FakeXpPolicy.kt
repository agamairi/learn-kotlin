package dev.agam.learn.fake

import dev.agam.learn.domain.XpPolicy
import dev.agam.learn.model.Exercise

/**
 * Standard experience point reward policy conforming to PLAN.md specifications:
 * - 10 XP for multiple choice, cloze, and predict output cards.
 * - 25 XP for write_function on initial attempt (15 XP upon retry).
 * - 50 XP per multi-part problem part.
 */
class FakeXpPolicy : XpPolicy {

    override fun xpForExercise(exercise: Exercise, isFirstAttempt: Boolean): Int {
        return when (exercise) {
            is Exercise.MultipleChoice,
            is Exercise.Cloze,
            is Exercise.PredictOutput -> 10
            is Exercise.WriteFunction -> if (isFirstAttempt) 25 else 15
            is Exercise.MultiPartProblem -> 50
        }
    }

    override fun xpForMultiPartPart(correct: Boolean): Int = if (correct) 50 else 0
}
