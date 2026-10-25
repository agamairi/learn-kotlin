package dev.agam.learn.ai

import dev.agam.learn.data.SecretStore
import dev.agam.learn.data.SettingsRepository
import kotlinx.coroutines.CancellationException

/** Shown in place of the AI controls when no API key has been saved. */
const val AI_API_KEY_MISSING_MESSAGE = "Add your API key in Settings"

/**
 * What the AI controls need to know before they are shown: whether the learner has saved a key,
 * and whether interview mode turns every AI entry point off.
 */
data class AiAccess(
    val hasApiKey: Boolean,
    val interviewMode: Boolean
) {
    /** True only when the AI controls can actually reach the tutor. */
    val isUsable: Boolean get() = hasApiKey && !interviewMode
}

/**
 * Reads the AI access state from the settings and the secret store. A secret store that fails
 * is treated as "no key saved" so the controls degrade to the add-key message.
 */
suspend fun loadAiAccess(settings: SettingsRepository, secrets: SecretStore): AiAccess {
    val hasKey = try {
        secrets.get()?.trim()?.isNotEmpty() == true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }
    return AiAccess(
        hasApiKey = hasKey,
        interviewMode = settings.getSettings().interviewMode
    )
}
