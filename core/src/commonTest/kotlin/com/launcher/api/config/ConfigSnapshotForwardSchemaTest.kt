package com.launcher.api.config

import family.wire.WireVersion

import com.launcher.api.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 009 T037 — SnapshotMigrator fails closed on future
 * schemaVersion (FR-043; plan §11 C-2 no silent default).
 */
class ConfigSnapshotForwardSchemaTest {

    @Test
    fun future_version_returns_unsupported_error() {
        val future = ConfigSnapshot(
            schemaVersion = WireVersion(99, 0),
            minReaderVersion = WireVersion(99, 0),
            minWriterVersion = WireVersion(99, 0),
            config = ConfigDocument(
                serverUpdatedAt = ServerTimestamp(epochSeconds = 1, nanoseconds = 0),
                lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
                presetId = "p",
                flows = emptyList(),
                contacts = emptyList(),
            ),
            recordedAt = 1,
            recordedFromDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
        )
        val r = SnapshotMigrator.migrate(future)
        assertTrue(r is Outcome.Failure)
        val err = r.error
        assertTrue(err is MigrationError.UnsupportedVersion)
        assertEquals(WireVersion(99, 0), err.requiredReaderVersion)
    }

    @Test
    fun v1_passes_through() {
        val v1 = ConfigSnapshot(
            schemaVersion = WireVersion(1, 0),
            config = ConfigDocument(
                serverUpdatedAt = ServerTimestamp(epochSeconds = 1, nanoseconds = 0),
                lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
                presetId = "p",
                flows = emptyList(),
                contacts = emptyList(),
            ),
            recordedAt = 1,
            recordedFromDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
        )
        val r = SnapshotMigrator.migrate(v1)
        assertTrue(r is Outcome.Success)
        assertEquals(v1, r.value)
    }
}
