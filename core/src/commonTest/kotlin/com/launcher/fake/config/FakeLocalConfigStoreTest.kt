package com.launcher.fake.config

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ElementId
import com.launcher.api.config.PendingLocalChanges
import com.launcher.api.config.ServerTimestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for FakeLocalConfigStore. The same test class will run
 * against the real SqlDelightLocalConfigStore in Phase 3 (T052) — both must
 * pass these assertions, per CLAUDE.md §6 fake/real parity.
 */
class FakeLocalConfigStoreTest {

    @Test
    fun read_applied_returns_null_when_empty() = runTest {
        val store = FakeLocalConfigStore()
        assertNull(store.readAppliedConfig("link-1"))
    }

    @Test
    fun write_then_read_applied_roundtrip() = runTest {
        val store = FakeLocalConfigStore()
        val config = sampleConfig()
        store.writeAppliedConfig("link-1", config)
        assertEquals(config, store.readAppliedConfig("link-1"))
    }

    @Test
    fun write_overwrites_previous() = runTest {
        val store = FakeLocalConfigStore()
        val c1 = sampleConfig(presetId = "v1")
        val c2 = sampleConfig(presetId = "v2")
        store.writeAppliedConfig("link-1", c1)
        store.writeAppliedConfig("link-1", c2)
        assertEquals("v2", store.readAppliedConfig("link-1")?.presetId)
    }

    @Test
    fun pending_lifecycle_write_read_clear() = runTest {
        val store = FakeLocalConfigStore()
        val pending = samplePending("link-1")
        store.writePending("link-1", pending)
        assertEquals(pending, store.readPending("link-1"))

        store.clearPending("link-1")
        assertNull(store.readPending("link-1"))
    }

    @Test
    fun pending_links_flow_reflects_state() = runTest {
        val store = FakeLocalConfigStore()
        // Initially empty
        assertEquals(emptySet(), store.pendingLinks().first())

        store.writePending("link-A", samplePending("link-A"))
        assertTrue("link-A" in store.pendingLinks().first())

        store.writePending("link-B", samplePending("link-B"))
        assertEquals(setOf("link-A", "link-B"), store.pendingLinks().first())

        store.clearPending("link-A")
        assertEquals(setOf("link-B"), store.pendingLinks().first())
    }

    @Test
    fun applied_and_pending_independent_per_link() = runTest {
        val store = FakeLocalConfigStore()
        store.writeAppliedConfig("link-1", sampleConfig(presetId = "applied-1"))
        store.writeAppliedConfig("link-2", sampleConfig(presetId = "applied-2"))
        store.writePending("link-1", samplePending("link-1"))

        assertEquals("applied-1", store.readAppliedConfig("link-1")?.presetId)
        assertEquals("applied-2", store.readAppliedConfig("link-2")?.presetId)
        assertEquals("link-1", store.readPending("link-1")?.linkId)
        assertNull(store.readPending("link-2"))
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun sampleConfig(presetId: String = "simple-launcher"): ConfigDocument =
        ConfigDocument(
            serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
            lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
            presetId = presetId,
            flows = emptyList(),
            contacts = emptyList(),
        )

    private fun samplePending(linkId: String): PendingLocalChanges = PendingLocalChanges(
        linkId = linkId,
        snapshotServerUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
        draftConfig = sampleConfig(presetId = "draft-$linkId"),
    )
}
