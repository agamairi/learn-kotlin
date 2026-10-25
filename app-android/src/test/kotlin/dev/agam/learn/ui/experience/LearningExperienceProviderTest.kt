package dev.agam.learn.ui.experience

import dev.agam.learn.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningExperienceProviderTest {

    @Test
    fun settingsObservationFailureFallsBackToQuietAndReducedMotion() = runBlocking {
        val settings = flow<AppSettings> {
            throw IllegalStateException("settings database unavailable")
        }.withSettingsFailureFallback().first()

        assertEquals(
            AppSettings(
                soundEffectsEnabled = false,
                hapticsEnabled = false,
                reducedMotionEnabled = true
            ),
            settings
        )
    }

    @Test
    fun settingsObservationCancellationPropagates() = runBlocking {
        val failure = runCatching {
            flow<AppSettings> {
                throw CancellationException("collection cancelled")
            }.withSettingsFailureFallback().first()
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }
}
