package com.launcher.adapters.config

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.launcher.adapters.config.db.ConfigStore
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ElementId
import com.launcher.api.config.PendingLocalChanges
import com.launcher.api.config.ServerTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real [SqlDelightLocalConfigStore] tests via in-memory JDBC SQLite driver (spec
 * 008 Phase 3 T052).
 *
 * **Same contract as [com.launcher.fake.config.FakeLocalConfigStoreTest]** —
 * per CLAUDE.md §6 mock-first parity guarantee. Если этот тест проходит и
 * fake test проходит — оба адаптера behaviour-equivalent.
 *
 * In-memory SQLite (`IN_MEMORY` URI) — каждый test gets fresh DB.
 */
class SqlDelightLocalConfigStoreTest {

    private fun makeStore(): SqlDelightLocalConfigStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ConfigStore.Schema.create(driver)
        val db = ConfigStore(driver)
        return SqlDelightLocalConfigStore(db, ioDispatcher = Dispatchers.Default)
    }

    @Test
    fun read_applied_returns_null_when_empty() = runTest {
        val store = makeStore()
        assertNull(store.readAppliedConfig("link-1"))
    }

    @Test
    fun write_then_read_applied_roundtrip() = runTest {
        val store = makeStore()
        val config = sampleConfig()
        store.writeAppliedConfig("link-1", config)
        assertEquals(config, store.readAppliedConfig("link-1"))
    }

    @Test
    fun write_overwrites_previous() = runTest {
        val store = makeStore()
        store.writeAppliedConfig("link-1", sampleConfig(presetId = "v1"))
        store.writeAppliedConfig("link-1", sampleConfig(presetId = "v2"))
        assertEquals("v2", store.readAppliedConfig("link-1")?.presetId)
    }

    @Test
    fun pending_lifecycle_write_read_clear() = runTest {
        val store = makeStore()
        val pending = samplePending("link-1")
        store.writePending("link-1", pending)
        assertEquals(pending, store.readPending("link-1"))

        store.clearPending("link-1")
        assertNull(store.readPending("link-1"))
    }

    @Test
    fun pending_links_flow_reflects_state() = runTest {
        val store = makeStore()
        // Initially empty.
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
        val store = makeStore()
        store.writeAppliedConfig("link-1", sampleConfig(presetId = "applied-1"))
        store.writeAppliedConfig("link-2", sampleConfig(presetId = "applied-2"))
        store.writePending("link-1", samplePending("link-1"))

        assertEquals("applied-1", store.readAppliedConfig("link-1")?.presetId)
        assertEquals("applied-2", store.readAppliedConfig("link-2")?.presetId)
        assertEquals("link-1", store.readPending("link-1")?.linkId)
        assertNull(store.readPending("link-2"))
    }

    @Test
    fun preserves_full_config_with_flows_and_contacts() = runTest {
        val store = makeStore()
        val flowId = ElementId("f1111111-1111-4111-8111-111111111111")
        val contactId = ElementId("c1111111-1111-4111-8111-111111111111")
        val config = ConfigDocument(
            serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 123),
            lastWriterDeviceId = "device-X",
            presetId = "simple-launcher",
            flows = listOf(
                com.launcher.api.config.Flow(
                    id = flowId,
                    title = "Главный",
                    slots = emptyList(),
                ),
            ),
            contacts = listOf(
                com.launcher.api.config.Contact(
                    id = contactId,
                    displayName = "Маша",
                    phoneNumber = "+71234567890",
                ),
            ),
        )
        store.writeAppliedConfig("link-1", config)
        val read = store.readAppliedConfig("link-1")
        assertEquals(config, read)
    }

    // ─── helpers (identical к FakeLocalConfigStoreTest для parity) ────────

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
