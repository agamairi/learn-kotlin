package dev.agam.learn.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
