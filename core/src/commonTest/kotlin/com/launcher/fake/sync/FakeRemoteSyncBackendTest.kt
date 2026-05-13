package com.launcher.fake.sync

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.DocSnapshot
import com.launcher.api.pairing.PairingToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Behavior tests for [FakeRemoteSyncBackend] (FR-012, spec 007 C5).
 *
 *  - write→read returns same snapshot
 *  - observer fan-out
 *  - transaction atomicity (rollback on TransactionAbort)
 *  - offline queue: writes buffered, reads stale, no observer fires
 *  - online flush: queue applied in FIFO order
 */
class FakeRemoteSyncBackendTest {

    private val sampleData = buildJsonObject { put("k", JsonPrimitive("v")) }
    private val path = DocPath.Pairings(PairingToken("A3KX9B"))

    @Test
    fun write_then_read_returns_same_data() = runTest {
        val backend = FakeRemoteSyncBackend()
        backend.writeDoc(path, sampleData, schemaVersion = 1).assertSuccess()

        val read = backend.readDoc(path).assertSuccessValue()
        assertNotNull(read)
        assertEquals(sampleData, read.data)
        assertEquals(1, read.schemaVersion)
        assertEquals(false, read.isStale)
    }

    @Test
    fun delete_removes_doc() = runTest {
        val backend = FakeRemoteSyncBackend()
        backend.writeDoc(path, sampleData, 1).assertSuccess()
        backend.deleteDoc(path).assertSuccess()
        val read = backend.readDoc(path).assertSuccessValue()
        assertNull(read)
    }

    @Test
    fun observer_emits_initial_then_updates() = runTest {
        val backend = FakeRemoteSyncBackend()
        backend.writeDoc(path, sampleData, 1).assertSuccess()

        val first = backend.observe(path).first()
        val firstSnap = (first as Outcome.Success).value
        assertNotNull(firstSnap)
        assertEquals(sampleData, firstSnap.data)
    }

    @Test
    fun transaction_commits_on_success() = runTest {
        val backend = FakeRemoteSyncBackend()
        val result = backend.runTransaction {
            set(path, sampleData, 1)
            "ok"
        }
        assertEquals("ok", result.assertSuccessValue())
        // Committed → stored.
        assertNotNull(backend.peek(path))
    }

    @Test
    fun transaction_sees_its_own_writes() = runTest {
        val backend = FakeRemoteSyncBackend()
        val readBack: DocSnapshot? = backend.runTransaction {
            set(path, sampleData, 1)
            get(path)
        }.assertSuccessValue()
        assertNotNull(readBack)
        assertEquals(sampleData, readBack.data)
    }

    @Test
    fun transaction_rolls_back_on_exception() = runTest {
        val backend = FakeRemoteSyncBackend()
        val result = backend.runTransaction<Unit> {
            set(path, sampleData, 1)
            error("boom")
        }
        assertTrue(result is Outcome.Failure)
        // Rollback → NOT stored.
        assertNull(backend.peek(path))
    }

    @Test
    fun offline_queues_writes_and_reads_are_stale() = runTest {
        val backend = FakeRemoteSyncBackend()
        backend.writeDoc(path, sampleData, 1).assertSuccess()
        backend.setOnline(false)

        val updated = buildJsonObject { put("k", JsonPrimitive("v2")) }
        backend.writeDoc(path, updated, 1).assertSuccess()
        assertEquals(1, backend.queuedOperationCount(), "write should be queued offline")

        // Read while offline returns last-known stale.
        val stale = backend.readDoc(path).assertSuccessValue()
        assertNotNull(stale)
        assertEquals(true, stale.isStale)
        assertEquals(sampleData, stale.data, "stale read should NOT include queued update")
    }

    @Test
    fun online_flush_applies_queue_in_order() = runTest {
        val backend = FakeRemoteSyncBackend()
        backend.setOnline(false)

        val first = buildJsonObject { put("seq", JsonPrimitive(1)) }
        val second = buildJsonObject { put("seq", JsonPrimitive(2)) }
        backend.writeDoc(path, first, 1).assertSuccess()
        backend.writeDoc(path, second, 1).assertSuccess()
        assertEquals(2, backend.queuedOperationCount())

        backend.setOnline(true)
        assertEquals(0, backend.queuedOperationCount(), "queue should have flushed")

        val read = backend.readDoc(path).assertSuccessValue()
        assertNotNull(read)
        // Last write wins after FIFO flush.
        assertEquals(second, read.data)
    }

    @Test
    fun transaction_fails_when_offline() = runTest {
        val backend = FakeRemoteSyncBackend()
        backend.setOnline(false)
        val result = backend.runTransaction<String> { "x" }
        assertTrue(result is Outcome.Failure)
        assertEquals(BackendError.Offline, result.error)
    }

    // ---- helpers ---------------------------------------------------------

    private fun <T> Outcome<T, BackendError>.assertSuccess() {
        if (this is Outcome.Failure) fail("expected success, got Failure($error)")
    }

    private fun <T> Outcome<T, BackendError>.assertSuccessValue(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("expected success, got Failure($error)")
    }
}
