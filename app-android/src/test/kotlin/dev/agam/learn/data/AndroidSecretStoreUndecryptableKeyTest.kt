package dev.agam.learn.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.agam.learn.ai.AI_API_KEY_MISSING_MESSAGE
import dev.agam.learn.ai.AiAccess
import dev.agam.learn.ai.AiProvider
import dev.agam.learn.ai.AiResult
import dev.agam.learn.ai.DynamicAiTutor
import dev.agam.learn.ai.loadAiAccess
import dev.agam.learn.fake.FakeSettingsRepository
import dev.agam.learn.model.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecretStoreUndecryptableKeyTest {

    private lateinit var database: LearnDatabase
    private lateinit var key1: SecretKey
    private lateinit var key2: SecretKey

    @Before
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LearnDatabase.Schema.create(driver)
        database = LearnDatabase(driver)

        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        key1 = keyGen.generateKey()
        key2 = keyGen.generateKey()
    }

    @Test
    fun restoredKeyWithDifferentKeystoreKeyIsTreatedAsNoKeyAndCiphertextIsDeleted() = runBlocking {
        val originalDeviceStore = AndroidSecretStore(database, keyProvider = { key1 })
        originalDeviceStore.set("sk-ant-original-secret-key")

        // Ciphertext exists in settings_kv
        val ciphertextBefore = database.settingsKvQueries
            .selectSettingByKey(AI_API_KEY_SETTING)
            .executeAsOneOrNull()
        assertTrue("Ciphertext should be saved", !ciphertextBefore.isNullOrBlank())

        // Restored device has a different Keystore key (key2)
        val restoredDeviceStore = AndroidSecretStore(database, keyProvider = { key2 })

        // Attempting to read undecryptable key must return null, not crash
        val recovered = restoredDeviceStore.get()
        assertNull("Undecryptable key must return null", recovered)

        // The bad ciphertext must be deleted from the database
        val ciphertextAfter = database.settingsKvQueries
            .selectSettingByKey(AI_API_KEY_SETTING)
            .executeAsOneOrNull()
        assertNull("Bad ciphertext must be deleted from database", ciphertextAfter)
    }

    @Test
    fun corruptCiphertextReturnsNullAndDeletesBadEntry() = runBlocking {
        // Manually insert malformed data into settings_kv
        database.settingsKvQueries.insertSetting(AI_API_KEY_SETTING, "corrupted_garbage_without_dot")

        val store = AndroidSecretStore(database, keyProvider = { key1 })
        val result = store.get()
        assertNull(result)

        val storedAfter = database.settingsKvQueries
            .selectSettingByKey(AI_API_KEY_SETTING)
            .executeAsOneOrNull()
        assertNull("Corrupt data must be deleted", storedAfter)
    }

    @Test
    fun encryptionRoundTripsAndGeneratesANewIvForEveryWrite() = runBlocking {
        val store = AndroidSecretStore(database, keyProvider = { key1 })

        store.set("synthetic-first-value")
        val firstRecord = storedRecord()
        store.set("synthetic-second-value")
        val secondRecord = storedRecord()

        assertFalse("Each encryption must use a fresh IV", firstRecord.iv.contentEquals(secondRecord.iv))
        assertTrue(firstRecord.ciphertext.isNotEmpty())
        assertEquals("synthetic-second-value", store.get())
    }

    @Test
    fun existingIvAndCiphertextRecordFormatRemainsReadable() = runBlocking {
        val iv = ByteArray(12) { index -> (index + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key1, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal("synthetic-legacy-value".encodeToByteArray())
        val legacyRecord = Base64.getEncoder().encodeToString(iv) + "." +
            Base64.getEncoder().encodeToString(ciphertext)
        database.settingsKvQueries.insertSetting(AI_API_KEY_SETTING, legacyRecord)

        val store = AndroidSecretStore(database, keyProvider = { key1 })
        assertEquals("synthetic-legacy-value", store.get())
    }

    @Test
    fun undecryptableKeyDegradesToMissingKeyInAiAccessAndTutor() = runBlocking {
        val originalStore = AndroidSecretStore(database, keyProvider = { key1 })
        originalStore.set("sk-ant-test-key")

        // New device cannot decrypt
        val restoredStore = AndroidSecretStore(database, keyProvider = { key2 })
        val settingsRepo = FakeSettingsRepository(
            AppSettings(selectedAiProvider = AiProvider.ANTHROPIC, interviewMode = false)
        )

        // AI access reports no key
        val access = loadAiAccess(settingsRepo, restoredStore)
        assertEquals(AiAccess(hasApiKey = false, interviewMode = false), access)
        assertFalse(access.isUsable)

        // AI tutor returns add-key message without crashing
        val tutor = DynamicAiTutor(settingsRepo, restoredStore)
        val dummyExercise = Exercise.MultipleChoice(
            id = "test-1",
            prompt = "What is Kotlin?",
            options = listOf("Language", "Island"),
            correctIndex = 0,
            explanation = ""
        )
        val errorResult = tutor.explainMistake(dummyExercise, "wrong", "right")
        assertTrue(errorResult is AiResult.Error)
        assertEquals(AI_API_KEY_MISSING_MESSAGE, (errorResult as AiResult.Error).message)
    }

    private fun storedRecord(): StoredRecord {
        val stored = database.settingsKvQueries
            .selectSettingByKey(AI_API_KEY_SETTING)
            .executeAsOneOrNull()
        requireNotNull(stored)
        val separator = stored.indexOf('.')
        assertTrue("Encrypted value keeps the existing IV.ciphertext format", separator > 0)
        return StoredRecord(
            iv = Base64.getDecoder().decode(stored.substring(0, separator)),
            ciphertext = Base64.getDecoder().decode(stored.substring(separator + 1))
        )
    }

    private data class StoredRecord(val iv: ByteArray, val ciphertext: ByteArray)
}
