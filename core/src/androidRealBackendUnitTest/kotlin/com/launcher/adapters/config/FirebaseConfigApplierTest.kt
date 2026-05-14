package com.launcher.adapters.config

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigDocumentWireFormat
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.ElementId
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.result.Outcome
import com.launcher.api.sync.DocPath
import com.launcher.fake.config.FakeLocalConfigStore
import com.launcher.fake.sync.FakeRemoteSyncBackend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [FirebaseConfigApplier] using FakeRemoteSyncBackend + FakeLocalConfigStore.
 *
 * Per spec 008 Phase 4 T061. Covers FR-021 (apply atomically), FR-023
 * (self-as-writer skip), FR-030/031 (publish /state.appliedConfigUpdatedAt).
 */
class FirebaseConfigApplierTest {

    @Test
    fun apply_writes_to_local_and_publishes_state() = runTest {
        val remote = FakeRemoteSyncBackend()
        val local = FakeLocalConfigStore()
        val applier = FirebaseConfigApplier(
            remoteSync = remote,
            localStore = local,
            selfDeviceIdProvider = { "self-device" },
        )

        // Seed remote /config/current via the SAME wire format the applier will read.
        val config = sampleConfig(writer = "admin-device-123")
        remote.writeDoc(
            path = DocPath.LinkConfig("link-1"),
            data = ConfigDocumentWireFormat.serialize(config),
            schemaVersion = config.schemaVersion,
        )

        val result = applier.applyFromRemote("link-1")

        assertTrue(result is Outcome.Success, "apply должен success-fully complete: $result")
        // Local store has the config.
        val readBack = local.readAppliedConfig("link-1")
        assertEquals(config.presetId, readBack?.presetId)
        // State published.
        val state = (result as Outcome.Success).value
        assertEquals(config.serverUpdatedAt, state.appliedConfigUpdatedAt)
        assertEquals(config.presetId, state.presetId)
    }

    @Test
    fun apply_self_as_writer_skip_FR_023() = runTest {
        val remote = FakeRemoteSyncBackend()
        val local = FakeLocalConfigStore()
        val applier = FirebaseConfigApplier(
            remoteSync = remote,
            localStore = local,
            selfDeviceIdProvider = { "self-device" },
        )

        // Config writer == self.
        val config = sampleConfig(writer = "self-device")
        remote.writeDoc(
            path = DocPath.LinkConfig("link-1"),
            data = ConfigDocumentWireFormat.serialize(config),
            schemaVersion = config.schemaVersion,
        )

        val result = applier.applyFromRemote("link-1")

        assertTrue(result is Outcome.Success)
        // FR-023: local store NOT written because we ourselves are the writer.
        assertNull(local.readAppliedConfig("link-1"))
    }

    @Test
    fun apply_missing_config_returns_BackendFailure_NotFound() = runTest {
        val remote = FakeRemoteSyncBackend()
        val local = FakeLocalConfigStore()
        val applier = FirebaseConfigApplier(
            remoteSync = remote,
            localStore = local,
            selfDeviceIdProvider = { "self-device" },
        )

        // No /config/current written — read returns null (Outcome.Success(null)),
        // which applier maps to BackendFailure(NotFound) per spec.
        val result = applier.applyFromRemote("link-1")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is ConfigSyncError.BackendFailure)
    }

    @Test
    fun apply_idempotent_repeated_calls() = runTest {
        val remote = FakeRemoteSyncBackend()
        val local = FakeLocalConfigStore()
        val applier = FirebaseConfigApplier(
            remoteSync = remote,
            localStore = local,
            selfDeviceIdProvider = { "self-device" },
        )

        val config = sampleConfig(writer = "admin-device-123")
        remote.writeDoc(
            path = DocPath.LinkConfig("link-1"),
            data = ConfigDocumentWireFormat.serialize(config),
            schemaVersion = config.schemaVersion,
        )

        val r1 = applier.applyFromRemote("link-1")
        val r2 = applier.applyFromRemote("link-1")

        assertTrue(r1 is Outcome.Success)
        assertTrue(r2 is Outcome.Success)
        // Same final state.
        assertEquals(config.presetId, local.readAppliedConfig("link-1")?.presetId)
    }

    private fun sampleConfig(writer: String): ConfigDocument = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
        lastWriterDeviceId = writer,
        presetId = "simple-launcher",
        flows = emptyList(),
        contacts = emptyList(),
    )
}
