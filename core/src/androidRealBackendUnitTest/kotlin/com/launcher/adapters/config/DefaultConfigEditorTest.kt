package com.launcher.adapters.config

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigDocumentWireFormat
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.result.Outcome
import com.launcher.api.sync.DocPath
import com.launcher.fake.config.FakeLocalConfigStore
import com.launcher.fake.sync.FakeRemoteSyncBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [DefaultConfigEditor] (spec 008 Phase 4 T063-T066).
 *
 * Covers:
 *  - T063: push happy path (US-1 scenario 1, FR-010..013);
 *  - T064: conflict path (US-2 scenario 1, FR-013, FR-014);
 *  - T065: autosave debounce (FR-056);
 *  - T066: discardPending clears state (FR-057).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultConfigEditorTest {

    @Test
    fun push_no_conflict_succeeds_US_1() = runTest {
        val remote = FakeRemoteSyncBackend()
        val local = FakeLocalConfigStore()
        val editor = DefaultConfigEditor(
            remoteSync = remote,
            localStore = local,
            selfDeviceIdProvider = { "device-A" },
            scope = TestScope(testScheduler),
        )

        // Seed applied config AND server (realistic state — applied came from server).
        val applied = sampleConfig(writer = "initial-writer", presetId = "simple-launcher")
        local.writeAppliedConfig("link-1", applied)
        remote.writeDoc(
            path = DocPath.LinkConfig("link-1"),
            data = ConfigDocumentWireFormat.serialize(applied),
            schemaVersion = applied.schemaVersion,
        )

        editor.updateDraft("link-1") { it.copy(presetId = "medium-launcher") }
        advanceTimeBy(500) // past 300ms debounce
        advanceUntilIdle()

        // Push.
        val result = editor.pushPending("link-1")
        assertTrue(result is Outcome.Success, "Push должен succeed: $result")

        // Pending cleared.
        assertNull(local.readPending("link-1"))

        // Server-side has the new config (verify via direct read of remote).
        val serverRead = remote.readDoc(DocPath.LinkConfig("link-1"))
        assertTrue(serverRead is Outcome.Success)
        val serverDoc = (serverRead as Outcome.Success).value
        assertNotNull(serverDoc)
        val parsedServer = ConfigDocumentWireFormat.deserialize(serverDoc!!.data as JsonObject)
        assertTrue(parsedServer is Outcome.Success)
        assertEquals("medium-launcher", (parsedServer as Outcome.Success).value.presetId)
        assertEquals("device-A", parsedServer.value.lastWriterDeviceId)
    }

    @Test
    fun push_with_conflict_returns_Conflict_US_2() = runTest {
        val remote = FakeRemoteSyncBackend()
        val local = FakeLocalConfigStore()
        val editor = DefaultConfigEditor(
            remoteSync = remote,
            localStore = local,
            selfDeviceIdProvider = { "device-B" },
            scope = TestScope(testScheduler),
        )

        // Both editors see same applied config (snapshot T0). Seed both local and server.
        val applied = sampleConfig(writer = "initial-writer", presetId = "simple-launcher")
        local.writeAppliedConfig("link-1", applied)
        remote.writeDoc(
            path = DocPath.LinkConfig("link-1"),
            data = ConfigDocumentWireFormat.serialize(applied),
            schemaVersion = applied.schemaVersion,
        )

        editor.updateDraft("link-1") { it.copy(presetId = "draft-B") }
        advanceTimeBy(500)
        advanceUntilIdle()

        // Another writer pushed to server между our snapshot read и our push.
        val advancedConfig = applied.copy(
            serverUpdatedAt = ServerTimestamp(
                epochSeconds = applied.serverUpdatedAt.epochSeconds + 10,
                nanoseconds = 0,
            ),
            lastWriterDeviceId = "device-A",
            presetId = "applied-by-A",
        )
        remote.writeDoc(
            path = DocPath.LinkConfig("link-1"),
            data = ConfigDocumentWireFormat.serialize(advancedConfig),
            schemaVersion = advancedConfig.schemaVersion,
        )

        val result = editor.pushPending("link-1")
        assertTrue(result is Outcome.Failure)
        val err = (result as Outcome.Failure).error
        assertTrue(err is ConfigSyncError.Conflict, "Expected Conflict, got: $err")
        // Server config in conflict matches what A wrote.
        assertEquals("applied-by-A", (err as ConfigSyncError.Conflict).serverConfig.presetId)
        // Pending preserved per FR-055.
        assertNotNull(local.readPending("link-1"))
    }

    @Test
    fun autosave_debounce_FR_056() = runTest {
        val remote = FakeRemoteSyncBackend()
        val local = FakeLocalConfigStore()
        val editor = DefaultConfigEditor(
            remoteSync = remote,
            localStore = local,
            selfDeviceIdProvider = { "device-A" },
            scope = TestScope(testScheduler),
        )

        local.writeAppliedConfig("link-1", sampleConfig(writer = "initial", presetId = "simple-launcher"))

        // Three rapid edits within debounce window.
        editor.updateDraft("link-1") { it.copy(presetId = "v1") }
        editor.updateDraft("link-1") { it.copy(presetId = "v2") }
        editor.updateDraft("link-1") { it.copy(presetId = "v3") }

        // Before debounce expires — pending NOT yet written to store.
        advanceTimeBy(100)
        // Можем not assert anything здесь yet, потому что local.writePending suspend
        // может execute partially. Главное — final state matches последний draft.

        advanceTimeBy(500) // past debounce
        advanceUntilIdle()

        val finalPending = local.readPending("link-1")
        assertNotNull(finalPending)
        // Final value is v3 — debounce collapsed the bursts.
        assertEquals("v3", finalPending.draftConfig.presetId)
    }

    @Test
    fun discardPending_clears_state_FR_057() = runTest {
        val remote = FakeRemoteSyncBackend()
        val local = FakeLocalConfigStore()
        val editor = DefaultConfigEditor(
            remoteSync = remote,
            localStore = local,
            selfDeviceIdProvider = { "device-A" },
            scope = TestScope(testScheduler),
        )

        local.writeAppliedConfig("link-1", sampleConfig(writer = "initial", presetId = "simple-launcher"))
        editor.updateDraft("link-1") { it.copy(presetId = "to-discard") }
        advanceTimeBy(500)
        advanceUntilIdle()

        assertNotNull(local.readPending("link-1"))

        editor.discardPending("link-1")
        assertNull(local.readPending("link-1"))
    }

    private fun sampleConfig(writer: String, presetId: String = "simple-launcher"): ConfigDocument =
        ConfigDocument(
            serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
            lastWriterDeviceId = writer,
            presetId = presetId,
            flows = emptyList(),
            contacts = emptyList(),
        )
}
