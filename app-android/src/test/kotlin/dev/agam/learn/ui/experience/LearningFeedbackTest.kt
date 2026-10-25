package dev.agam.learn.ui.experience

import dev.agam.learn.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningFeedbackTest {

    @Test
    fun answersAndMilestonesChooseDistinctSoundAndHapticEvents() {
        val effects = RecordingEffects()
        val feedback = ConfigurableLearningFeedback(effects)
        feedback.updateSettings(AppSettings())

        feedback.onAnswer(correct = true)
        feedback.onAnswer(correct = false)
        feedback.onMilestone()

        assertEquals(
            listOf(LearningSound.CORRECT, LearningSound.INCORRECT, LearningSound.MILESTONE),
            effects.sounds
        )
        assertEquals(
            listOf(LearningHaptic.SUCCESS, LearningHaptic.ERROR, LearningHaptic.MILESTONE),
            effects.haptics
        )
    }

    @Test
    fun soundAndHapticsPreferencesGateTheirOwnFeedbackChannels() {
        val effects = RecordingEffects()
        val feedback = ConfigurableLearningFeedback(effects)

        feedback.updateSettings(AppSettings(soundEffectsEnabled = false, hapticsEnabled = true))
        feedback.onAnswer(correct = false)
        assertEquals(emptyList<LearningSound>(), effects.sounds)
        assertEquals(listOf(LearningHaptic.ERROR), effects.haptics)

        effects.haptics.clear()
        feedback.updateSettings(AppSettings(soundEffectsEnabled = true, hapticsEnabled = false))
        feedback.onMilestone()
        assertEquals(listOf(LearningSound.MILESTONE), effects.sounds)
        assertEquals(emptyList<LearningHaptic>(), effects.haptics)
    }

    private class RecordingEffects : LearningFeedbackEffects {
        val sounds = mutableListOf<LearningSound>()
        val haptics = mutableListOf<LearningHaptic>()

        override fun play(sound: LearningSound) {
            sounds += sound
        }

        override fun vibrate(haptic: LearningHaptic) {
            haptics += haptic
        }
    }
}
