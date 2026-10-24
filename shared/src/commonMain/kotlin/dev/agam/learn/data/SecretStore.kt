package dev.agam.learn.data

/** Secure storage for the optional AI provider API key. */
interface SecretStore {
    /** Returns the API key, or null when no key has been saved. */
    suspend fun get(): String?

    /** Encrypts and stores an API key without exposing it to logs or exports. */
    suspend fun set(value: String)

    /** Removes the saved key and any associated encrypted payload. */
    suspend fun delete()
}

/** Creates the platform-specific secret store for a database. */
expect fun createSecretStore(database: LearnDatabase): SecretStore

const val AI_API_KEY_SETTING = "secret.ai_api_key"
