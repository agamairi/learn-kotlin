package dev.agam.learn.ai

/** Supplies a locally stored API key without making the tutor own secret storage. */
fun interface SecretStore {
    suspend fun getApiKey(): String?
}
