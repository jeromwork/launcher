package com.launcher.api.config

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
        assertEquals(1, parsed.snapshotSchemaVersion)
    }

    @Test
    fun snapshot_carries_supported_version_constant() {
        val s = sampleSnapshot()
        assertEquals(ConfigSnapshot.SUPPORTED_SNAPSHOT_SCHEMA_VERSION, s.snapshotSchemaVersion)
    }

    @Test
    fun v1_reader_reads_v1_minimal() {
        val wire = """
            {
              "snapshotSchemaVersion": 1,
              "config": {
                "schemaVersion": 1,
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
        assertEquals(1, parsed.snapshotSchemaVersion)
        assertEquals(100L, parsed.recordedAt)
    }
}
