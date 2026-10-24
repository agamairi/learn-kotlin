package dev.agam.learn.data

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JvmSecretStoreTest {
    @Test
    fun inMemorySecretStoreCanReplaceAndDeleteAKey() = runTest {
        val store = InMemorySecretStore()
        assertNull(store.get())
        store.set("not-logged")
        assertEquals("not-logged", store.get())
        store.delete()
        assertNull(store.get())
    }

    @Test
    fun reopeningExistingFileDatabaseDoesNotThrow() {
        val tempFile = File.createTempFile("learn-test-", ".db")
        tempFile.deleteOnExit()
        val factory = LearnDatabaseDriverFactory()

        val driver1 = factory.createDriver(tempFile.absolutePath)
        val db1 = LearnDatabase(driver1)
        assertNotNull(db1)
        driver1.close()

        // Reopening the same file must not throw "table already exists"
        val driver2 = factory.createDriver(tempFile.absolutePath)
        val db2 = LearnDatabase(driver2)
        assertNotNull(db2)
        driver2.close()

        tempFile.delete()
    }
}
