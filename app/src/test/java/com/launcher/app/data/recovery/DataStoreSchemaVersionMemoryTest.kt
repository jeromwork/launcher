package com.launcher.app.data.recovery

import androidx.test.core.app.ApplicationProvider
import family.keys.api.SchemaVersionMemory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric тесты для [DataStoreSchemaVersionMemory] (T122p, H-2 acceptance).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class DataStoreSchemaVersionMemoryTest {

    private fun make() = DataStoreSchemaVersionMemory(
        context = ApplicationProvider.getApplicationContext()
    )

    @Test
    fun freshMemoryReturnsNull() = runBlocking {
        val mem = make()
        assertNull(mem.lastSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT))
    }

    @Test
    fun recordAndReadSameVersion() = runBlocking {
        val mem = make()
        mem.recordSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT, 1)
        assertEquals(1, mem.lastSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT))
    }

    @Test
    fun higherVersionOverwrites() = runBlocking {
        val mem = make()
        mem.recordSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT, 1)
        mem.recordSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT, 5)
        assertEquals(5, mem.lastSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT))
    }

    @Test
    fun lowerVersionDoesNotOverwrite() = runBlocking {
        // Это ключевой H-2 invariant: monotonically increasing.
        val mem = make()
        mem.recordSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT, 5)
        mem.recordSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT, 1)
        assertEquals(5, mem.lastSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT))
    }

    @Test
    fun blobKindsTrackedIndependently() = runBlocking {
        val mem = make()
        mem.recordSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT, 3)
        mem.recordSeenVersion("uid-x", SchemaVersionMemory.KIND_CONFIG_BLOB, 7)
        assertEquals(3, mem.lastSeenVersion("uid-x", SchemaVersionMemory.KIND_RECOVERY_VAULT))
        assertEquals(7, mem.lastSeenVersion("uid-x", SchemaVersionMemory.KIND_CONFIG_BLOB))
    }

    @Test
    fun uidsTrackedIndependently() = runBlocking {
        val mem = make()
        mem.recordSeenVersion("uid-a", SchemaVersionMemory.KIND_RECOVERY_VAULT, 2)
        mem.recordSeenVersion("uid-b", SchemaVersionMemory.KIND_RECOVERY_VAULT, 8)
        assertEquals(2, mem.lastSeenVersion("uid-a", SchemaVersionMemory.KIND_RECOVERY_VAULT))
        assertEquals(8, mem.lastSeenVersion("uid-b", SchemaVersionMemory.KIND_RECOVERY_VAULT))
    }
}
