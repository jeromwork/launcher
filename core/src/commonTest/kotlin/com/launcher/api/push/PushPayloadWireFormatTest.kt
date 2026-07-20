package com.launcher.api.push

import family.wire.WireVersion

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wire-format tests for FCM data-message per
 * [`contracts/fcm-payload.md`](specs/007-pairing-and-firebase-channel/contracts/fcm-payload.md)
 * v1.0.0. Note the data-map shape: ALL values are stringified by FCM API.
 */
class PushPayloadWireFormatTest {

    @Test
    fun roundtrip_config_changed() {
        val original = PushPayload(
            type = PushType.ConfigChanged,
            linkId = "abc123XYZ",
        )
        val encoded = PushPayloadWireFormat.encode(original)
        assertEquals("1.0", encoded["schemaVersion"])
        assertEquals("config-changed", encoded["type"])
        assertEquals("abc123XYZ", encoded["linkId"])
        val parsed = PushPayloadWireFormat.parse(encoded)
        assertNotNull(parsed)
        assertEquals(original, parsed)
    }

    @Test
    fun roundtrip_command_issued_preserves_cmdId() {
        val original = PushPayload(
            type = PushType.CommandIssued,
            linkId = "abc123XYZ",
            extra = buildJsonObject { put("cmdId", JsonPrimitive("cmd-0001")) },
        )
        val encoded = PushPayloadWireFormat.encode(original)
        assertEquals("cmd-0001", encoded["cmdId"])
        val parsed = PushPayloadWireFormat.parse(encoded)
        assertNotNull(parsed)
        assertEquals(PushType.CommandIssued, parsed.type)
        assertEquals("cmd-0001", (parsed.extra?.get("cmdId") as? JsonPrimitive)?.content)
    }

    @Test
    fun unknown_type_drops() {
        // Old Managed app receiving a payload from a future server with a new
        // type — must drop silently per contract §Backward compatibility.
        val malformed = mapOf(
            "schemaVersion" to "1.0", "minReaderVersion" to "1.0", "minWriterVersion" to "1.0",
            "type" to "incoming-call-future-type",
            "linkId" to "abc123XYZ",
        )
        assertNull(PushPayloadWireFormat.parse(malformed))
    }

    @Test
    fun malformed_schemaVersion_drops() {
        val malformed = mapOf(
            "schemaVersion" to "not-an-int",
            "type" to "config-changed",
            "linkId" to "abc123XYZ",
        )
        assertNull(PushPayloadWireFormat.parse(malformed))
    }

    @Test
    fun future_schema_version_drops() {
        val futureVersion = mapOf(
            "schemaVersion" to "999.0", "minReaderVersion" to "999.0", "minWriterVersion" to "999.0",
            "type" to "config-changed",
            "linkId" to "abc123XYZ",
        )
        assertNull(PushPayloadWireFormat.parse(futureVersion))
    }

    @Test
    fun missing_linkId_drops() {
        val malformed = mapOf(
            "schemaVersion" to "1.0", "minReaderVersion" to "1.0", "minWriterVersion" to "1.0",
            "type" to "config-changed",
        )
        assertNull(PushPayloadWireFormat.parse(malformed))
    }

    @Test
    fun parseSchemaVersionOnly_extracts_from_data_map() {
        val data = mapOf("schemaVersion" to "1.0", "minReaderVersion" to "1.0", "minWriterVersion" to "1.0", "type" to "config-changed", "linkId" to "x")
        assertEquals(WireVersion(1, 0), PushPayloadWireFormat.parseSchemaVersionOnly(data))
    }

    @Test
    fun fcm_receiver_contract_routes_to_wire_format() {
        // FcmReceiverContract is the seam used by FirebaseMessagingService —
        // verify it delegates correctly.
        val data = mapOf(
            "schemaVersion" to "1.0", "minReaderVersion" to "1.0", "minWriterVersion" to "1.0",
            "type" to "config-changed",
            "linkId" to "abc",
        )
        val payload = FcmReceiverContract.parseFcmDataMap(data)
        assertNotNull(payload)
        assertEquals(PushType.ConfigChanged, payload.type)
        assertTrue(payload.extra == null, "no extras for config-changed")
    }
}
