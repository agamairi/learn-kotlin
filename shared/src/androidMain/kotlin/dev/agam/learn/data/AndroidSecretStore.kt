package dev.agam.learn.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android Keystore-backed AES-256-GCM implementation of [SecretStore]. */
class AndroidSecretStore(
    private val database: LearnDatabase
) : SecretStore {
    private val settings = database.settingsKvQueries
    private val lock = Any()

    override suspend fun get(): String? = synchronized(lock) {
        val stored = settings.selectSettingByKey(AI_API_KEY_SETTING).executeAsOneOrNull()
            ?: return@synchronized null
        try {
            val separator = stored.indexOf('.')
            require(separator > 0) { "Invalid encrypted secret" }
            val iv = Base64.decode(stored.substring(0, separator), Base64.DEFAULT)
            val ciphertext = Base64.decode(stored.substring(separator + 1), Base64.DEFAULT)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext).decodeToString()
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun set(value: String) = synchronized(lock) {
        val iv = ByteArray(GCM_IV_BYTES).also(SecureRandomHolder.random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        val encoded = Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        settings.insertSetting(AI_API_KEY_SETTING, encoded)
    }

    override suspend fun delete() = synchronized(lock) {
        settings.deleteSetting(AI_API_KEY_SETTING)
        val keyStore = keyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun key(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private object SecureRandomHolder {
        val random = java.security.SecureRandom()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "dev.agam.learn.ai_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
    }
}

/** Creates an Android SQLDelight driver from an Android context. */
actual class LearnDatabaseDriverFactory actual constructor(context: Any?) {
    private val applicationContext: Context = requireNotNull(context as? Context) {
        "Android database driver requires an Android Context"
    }.applicationContext

    actual fun createDriver(databaseName: String): app.cash.sqldelight.db.SqlDriver =
        AndroidSqliteDriver(LearnDatabase.Schema, applicationContext, databaseName)
}

actual fun createSecretStore(database: LearnDatabase): SecretStore = AndroidSecretStore(database)
