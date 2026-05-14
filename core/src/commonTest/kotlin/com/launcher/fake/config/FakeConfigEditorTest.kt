package com.launcher.fake.config

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeConfigEditorTest {

    @Test
    fun push_no_conflict_succeeds_US_1_scenario_1() = runTest {
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "device-A")
        val initial = sampleConfig(presetId = "simple-launcher")
        editor.seedServer("link-1", initial)

        // Apply initial to local applied so updateDraft has a base.
        store.writeAppliedConfig("link-1", initial)

        editor.updateDraft("link-1") { it.copy(presetId = "medium-launcher") }
        assertNotNull(store.readPending("link-1"), "Pending должен существовать после updateDraft")

        val result = editor.pushPending("link-1")
        assertTrue(result is Outcome.Success, "Push should succeed with matching snapshot: $result")
        assertEquals("medium-launcher", editor.serverConfig("link-1")?.presetId)
        assertEquals("device-A", editor.serverConfig("link-1")?.lastWriterDeviceId)
        assertNull(store.readPending("link-1"), "Pending should be cleared после успешного push")
    }

    @Test
    fun push_with_conflict_returns_Conflict_US_2_scenario_1() = runTest {
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "device-B")
        val initialConfig = sampleConfig(presetId = "simple-launcher")
        editor.seedServer("link-1", initialConfig)
        store.writeAppliedConfig("link-1", initialConfig)

        // Editor B starts editing.
        editor.updateDraft("link-1") { it.copy(presetId = "draft-B") }

        // Meanwhile, editor A pushes — server advances.
        val advanced = initialConfig.copy(
            serverUpdatedAt = ServerTimestamp(epochSeconds = initialConfig.serverUpdatedAt.epochSeconds + 10, nanoseconds = 0),
            lastWriterDeviceId = "device-A",
            presetId = "applied-by-A",
        )
        editor.bumpServerUpdatedAt("link-1", advanced)

        // Now editor B pushes with stale snapshot — should conflict.
        val result = editor.pushPending("link-1")

        assertTrue(result is Outcome.Failure)
        val err = (result as Outcome.Failure).error
        assertTrue(err is ConfigSyncError.Conflict)
        assertEquals("applied-by-A", (err as ConfigSyncError.Conflict).serverConfig.presetId)
        // Pending still saved (FR-055).
        assertNotNull(store.readPending("link-1"))
    }

    @Test
    fun discard_clears_pending_FR_057_action() = runTest {
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "device-A")
        editor.seedServer("link-1", sampleConfig())
        store.writeAppliedConfig("link-1", sampleConfig())

        editor.updateDraft("link-1") { it.copy(presetId = "draft") }
        assertNotNull(store.readPending("link-1"))

        editor.discardPending("link-1")
        assertNull(store.readPending("link-1"), "Discard должен очистить pending")
    }

    @Test
    fun cancel_merge_preserves_pending_FR_055() = runTest {
        // FR-055: if user closes merge UI без resolution (= не вызывает pushPending
        // after Conflict), pending stays untouched. We model this как: получили
        // Outcome.Failure(Conflict), не вызвали discardPending — pending всё ещё там.
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "device-B")
        editor.seedServer("link-1", sampleConfig())
        store.writeAppliedConfig("link-1", sampleConfig())

        editor.updateDraft("link-1") { it.copy(presetId = "draft-B") }
        val advanced = sampleConfig().copy(
            serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166410L, nanoseconds = 0),
            lastWriterDeviceId = "device-A",
        )
        editor.bumpServerUpdatedAt("link-1", advanced)

        val result = editor.pushPending("link-1")
        assertTrue(result is Outcome.Failure)
        // User cancels merge UI — no discardPending() call.
        assertNotNull(store.readPending("link-1"), "Pending preserved per FR-055")
    }

    @Test
    fun pendingDraft_flow_reflects_updates() = runTest {
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "device-A")
        editor.seedServer("link-1", sampleConfig())
        store.writeAppliedConfig("link-1", sampleConfig())

        assertNull(editor.pendingDraft("link-1").first(), "Initially no pending")

        editor.updateDraft("link-1") { it.copy(presetId = "draft-x") }
        assertEquals("draft-x", editor.pendingDraft("link-1").first()?.presetId)
    }

    private fun sampleConfig(presetId: String = "simple-launcher"): ConfigDocument = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
        lastWriterDeviceId = "initial-writer",
        presetId = presetId,
        flows = emptyList(),
        contacts = emptyList(),
    )
}
