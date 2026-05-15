package com.launcher.fake.config

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.link.PartialReason
import com.launcher.api.result.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeConfigApplierTest {

    @Test
    fun apply_writes_to_local_and_returns_state() = runTest {
        val store = FakeLocalConfigStore()
        val applier = FakeConfigApplier(localStore = store, selfDeviceId = "self-device")
        val remoteConfig = sampleConfig(writerDevice = "admin-device-123")
        applier.seedRemote("link-1", remoteConfig)

        val result = applier.applyFromRemote("link-1")

        assertTrue(result is Outcome.Success)
        // Local store now has the config.
        assertEquals(remoteConfig, store.readAppliedConfig("link-1"))
        // /state.appliedConfigUpdatedAt mirrors /config.serverUpdatedAt.
        assertEquals(remoteConfig.serverUpdatedAt, (result as Outcome.Success).value.appliedConfigUpdatedAt)
    }

    @Test
    fun apply_idempotent_when_called_twice() = runTest {
        val store = FakeLocalConfigStore()
        val applier = FakeConfigApplier(localStore = store, selfDeviceId = "self-device")
        val config = sampleConfig(writerDevice = "admin-device-123")
        applier.seedRemote("link-1", config)

        val r1 = applier.applyFromRemote("link-1")
        val r2 = applier.applyFromRemote("link-1")

        assertTrue(r1 is Outcome.Success)
        assertTrue(r2 is Outcome.Success)
        assertEquals(config, store.readAppliedConfig("link-1"))
    }

    @Test
    fun apply_self_as_writer_skip_FR_023() = runTest {
        // Если эта Managed только что pushed config (lastWriterDeviceId = self),
        // applier должен skip local write (apply уже сделан до push'а).
        val store = FakeLocalConfigStore()
        val applier = FakeConfigApplier(localStore = store, selfDeviceId = "self-device")
        val config = sampleConfig(writerDevice = "self-device") // self
        applier.seedRemote("link-1", config)

        val result = applier.applyFromRemote("link-1")

        assertTrue(result is Outcome.Success)
        // Local store NOT written (skip).
        assertEquals(null, store.readAppliedConfig("link-1"))
    }

    @Test
    fun apply_partial_returns_ApplyPartial_with_reasons() = runTest {
        val store = FakeLocalConfigStore()
        val applier = FakeConfigApplier(localStore = store, selfDeviceId = "self-device")
        applier.seedRemote("link-1", sampleConfig(writerDevice = "admin-device-123"))
        applier.forcePartialReasons(listOf(PartialReason.ProviderUnavailable))

        val result = applier.applyFromRemote("link-1")

        assertTrue(result is Outcome.Failure)
        val err = (result as Outcome.Failure).error
        assertTrue(err is ConfigSyncError.ApplyPartial)
        assertEquals(listOf(PartialReason.ProviderUnavailable), (err as ConfigSyncError.ApplyPartial).reasons)
    }

    private fun sampleConfig(writerDevice: String): ConfigDocument = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
        lastWriterDeviceId = writerDevice,
        presetId = "simple-launcher",
        flows = emptyList(),
        contacts = emptyList(),
    )
}
