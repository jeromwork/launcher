package com.launcher.api.config

import family.wire.WireVersion

import com.launcher.api.result.Outcome
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 009 T036/T038 — ConfigSnapshot envelope roundtrip + backward-compat
 * smoke (FR-036, contracts/config-history.md, CLAUDE.md rule 5).
 */
class ConfigSnapshotRoundtripTest {

    private val json: Json = WireFormatJson.json

    private fun sampleSnapshot(): ConfigSnapshot = ConfigSnapshot(
        config = ConfigDocument(
            serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
            lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
            presetId = "simple-launcher",
            flows = emptyList(),
            contacts = emptyList(),
        ),
        recordedAt = 1747166400123L,
        recordedFromDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
    )

    @Test
    fun roundtrip_default_v1() {
        val original = sampleSnapshot()
        val wire = json.encodeToString(original)
        val parsed = json.decodeFromString<ConfigSnapshot>(wire)
        assertEquals(original, parsed)
        assertEquals(WireVersion(1, 0), parsed.schemaVersion)
    }

    @Test
    fun snapshot_carries_supported_version_constant() {
        val s = sampleSnapshot()
        assertEquals(ConfigSnapshot.SCHEMA_VERSION, s.schemaVersion)
    }

    @Test
    fun v1_reader_reads_v1_minimal() {
        val wire = """
            {
              "snapshotSchemaVersion": "1.0",
              "snapshotMinReaderVersion": "1.0",
              "snapshotMinWriterVersion": "1.0",
              "config": {
                "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
                "serverUpdatedAt": {"epochSeconds": 1, "nanoseconds": 0},
                "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
                "presetId": "p",
                "flows": [],
                "contacts": []
              },
              "recordedAt": 100,
              "recordedFromDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a"
            }
        """.trimIndent()
        val parsed = json.decodeFromString<ConfigSnapshot>(wire)
        assertEquals(WireVersion(1, 0), parsed.schemaVersion)
        assertEquals(100L, parsed.recordedAt)
    }
}
