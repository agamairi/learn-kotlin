package dev.agam.learn.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the platform Keystore path using synthetic values and an isolated database/key alias. */
@RunWith(AndroidJUnit4::class)
class AndroidSecretStoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var driver: AndroidSqliteDriver
    private lateinit var database: LearnDatabase
    private lateinit var testAlias: String
    private val stores = mutableListOf<AndroidSecretStore>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        databaseName = "secret-store-test-$suffix.db"
        testAlias = "dev.agam.learn.instrumentation.$suffix"
        driver = AndroidSqliteDriver(LearnDatabase.Schema, context, databaseName)
        database = LearnDatabase(driver)
    }

    @After
    fun tearDown() {
        if (::driver.isInitialized) {
            runBlocking {
                stores.forEach { runCatching { it.delete() } }
            }
            runCatching { driver.close() }
        }
        if (::context.isInitialized && ::databaseName.isInitialized) {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun realKeystoreEncryptsWithFreshIvsAndSupportsOverwriteAndDelete() = runBlocking {
        val store = newStore(testAlias)
        store.set("synthetic-instrumentation-value-one")

        val firstRecord = encryptedRecord()
        assertFalse(firstRecord.encoded.contains("synthetic-instrumentation-value-one"))
        assertEquals(12, firstRecord.iv.size)
        assertTrue(firstRecord.ciphertext.isNotEmpty())
        assertEquals("synthetic-instrumentation-value-one", store.get())

        store.set("synthetic-instrumentation-value-two")
        val secondRecord = encryptedRecord()
        assertFalse(secondRecord.encoded.contains("synthetic-instrumentation-value-two"))
        assertFalse("Provider-generated IVs must be fresh", firstRecord.iv.contentEquals(secondRecord.iv))
        assertEquals("synthetic-instrumentation-value-two", store.get())

        store.delete()
        assertNull(store.get())
        assertFalse(androidKeyStore().containsAlias(testAlias))
    }

    @Test
    fun malformedAndWrongKeyRecordsFailClosed() = runBlocking {
        val store = newStore(testAlias)
        database.settingsKvQueries.insertSetting(AI_API_KEY_SETTING, "not-a-valid-record")
        assertNull(store.get())
        assertNull(storedValue())

        store.set("synthetic-wrong-key-probe")
        val wrongKeyStore = newStore("$testAlias.wrong-key")
        assertNull(wrongKeyStore.get())
        assertNull("Undecryptable values are discarded", storedValue())
    }

    private fun newStore(alias: String): AndroidSecretStore =
        AndroidSecretStore(database = database, keyAlias = alias).also(stores::add)

    private fun encryptedRecord(): EncryptedRecord {
        val encoded = requireNotNull(storedValue())
        val separator = encoded.indexOf('.')
        assertTrue("Stored value retains the existing IV.ciphertext format", separator > 0)
        return EncryptedRecord(
            encoded = encoded,
            iv = Base64.getDecoder().decode(encoded.substring(0, separator)),
            ciphertext = Base64.getDecoder().decode(encoded.substring(separator + 1))
        )
    }

    private fun storedValue(): String? = database.settingsKvQueries
        .selectSettingByKey(AI_API_KEY_SETTING)
        .executeAsOneOrNull()

    private fun androidKeyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private data class EncryptedRecord(
        val encoded: String,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )
}
