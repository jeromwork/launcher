package com.launcher.api.config

import family.wire.WireVersion

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wire-format tests for `/links/{linkId}/config/current` per
 * [`contracts/config.md`](../../../../specs/008-bidirectional-config-sync/contracts/config.md) v1.
 *
 * Covers FR-005 (roundtrip + backward-compat read) and SC-005.
 *
 * Fixture strings inlined for KMP `commonTest` compatibility — multi-platform
 * resource-loading is non-trivial (Android filesDir vs iOS bundle). The
 * physical fixture files в `commonTest/resources/spec008-fixtures/` serve as
 * canonical reference for adapters.
 */
class ConfigDocumentWireFormatTest {

    private val json = WireFormatJson.json

    @Test
    fun roundtrip_minimal() {
        val original = ConfigDocument(
            serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
            lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
            presetId = "simple-launcher",
            flows = emptyList(),
            contacts = emptyList(),
        )
        val wire = ConfigDocumentWireFormat.serialize(original)
        val parsed = ConfigDocumentWireFormat.deserialize(wire).orFail()
        assertEquals(original, parsed)
    }

    @Test
    fun roundtrip_full_with_flows_and_contacts() {
        val flowId = ElementId("f1111111-1111-4111-8111-111111111111")
        val slotId = ElementId("11111111-1111-4111-8111-111111111111")
        val contactId = ElementId("c1111111-1111-4111-8111-111111111111")
        val original = ConfigDocument(
            serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 123456789),
            lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
            presetId = "simple-launcher",
            flows = listOf(
                Flow(
                    id = flowId,
                    title = "Главный",
                    slots = listOf(
                        Slot(
                            id = slotId,
                            kind = SlotKind.Call,
                            args = buildJsonObject { put("contactId", contactId.value) },
                        ),
                    ),
                ),
            ),
            contacts = listOf(
                Contact(
                    id = contactId,
                    displayName = "Маша",
                    phoneNumber = "+71234567890",
                ),
            ),
        )
        val wire = ConfigDocumentWireFormat.serialize(original)
        val parsed = ConfigDocumentWireFormat.deserialize(wire).orFail()
        assertEquals(original, parsed)
    }

    @Test
    fun backwardCompat_v0_synthetic_reads_with_default_arrays() {
        // FR-005 backward-compat: «v0»-shaped doc (no flows/contacts fields) must read OK.
        // Defaults: empty lists (matches FR-006 additive policy).
        val wire = """
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "serverUpdatedAt": {"epochSeconds": 1747166400, "nanoseconds": 0},
              "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
              "presetId": "simple-launcher"
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire) as JsonObject
        val parsed = ConfigDocumentWireFormat.deserialize(element).orFail()
        assertEquals(emptyList(), parsed.flows)
        assertEquals(emptyList(), parsed.contacts)
        assertEquals("simple-launcher", parsed.presetId)
    }

    @Test
    fun forwardCompat_unknown_extra_fields_tolerated() {
        // Future versions may add fields. v1 reader must ignore them, not crash.
        val wire = """
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "serverUpdatedAt": {"epochSeconds": 1747166400, "nanoseconds": 0},
              "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
              "presetId": "simple-launcher",
              "flows": [],
              "contacts": [],
              "futureField": "to-be-ignored"
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire) as JsonObject
        val parsed = ConfigDocumentWireFormat.deserialize(element).orFail()
        assertEquals(WireVersion(1, 0), parsed.schemaVersion)
    }

    @Test
    fun unknown_slot_kind_fails_closed() {
        // CHK009: unknown discriminator → Failure, not silent skip / not crash.
        val wire = """
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "serverUpdatedAt": {"epochSeconds": 1747166400, "nanoseconds": 0},
              "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
              "presetId": "simple-launcher",
              "flows": [
                {
                  "id": "f1111111-1111-4111-8111-111111111111",
                  "title": "X",
                  "slots": [{"id": "44444444-4444-4444-8444-444444444444", "kind": "unknown-future-kind", "args": {}}]
                }
              ],
              "contacts": []
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire) as JsonObject
        val result = ConfigDocumentWireFormat.deserialize(element)
        assertTrue(result is Outcome.Failure, "Expected Failure for unknown slot kind, got: $result")
    }

    @Test
    fun newer_writer_alone_is_accepted() {
        // §3 — schemaVersion is diagnostics only. A config written by a much newer build stays
        // readable while it does not demand a newer reader. Before the conversion this was
        // refused outright, which would have blocked an admin on an older phone from reading a
        // config their own newer device had just written.
        val wire = """
            {
              "schemaVersion": "999.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "serverUpdatedAt": {"epochSeconds": 1747166400, "nanoseconds": 0},
              "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
              "presetId": "simple-launcher",
              "flows": [],
              "contacts": []
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire) as JsonObject
        val result = ConfigDocumentWireFormat.deserialize(element)
        assertTrue(result is Outcome.Success, "Expected Success, got: $result")
    }

    @Test
    fun document_requiring_a_newer_reader_is_rejected() {
        val wire = """
            {
              "schemaVersion": "999.0", "minReaderVersion": "999.0", "minWriterVersion": "999.0",
              "serverUpdatedAt": {"epochSeconds": 1747166400, "nanoseconds": 0},
              "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
              "presetId": "simple-launcher",
              "flows": [],
              "contacts": []
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire) as JsonObject
        val result = ConfigDocumentWireFormat.deserialize(element)
        assertTrue(result is Outcome.Failure, "Expected Failure for future schemaVersion, got: $result")
    }

    @Test
    fun missing_required_fields_rejected() {
        val wire = """{"schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0"}"""
        val element = json.parseToJsonElement(wire) as JsonObject
        val result = ConfigDocumentWireFormat.deserialize(element)
        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun parseSchemaVersionOnly_returnsTheDottedVersion() {
        val element = json.parseToJsonElement("""{"schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0", "presetId": "x"}""") as JsonObject
        assertEquals(WireVersion(1, 0), ConfigDocumentWireFormat.parseSchemaVersionOnly(element))
    }

    @Test
    fun invalid_uuid_id_rejected() {
        val wire = """
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "serverUpdatedAt": {"epochSeconds": 1747166400, "nanoseconds": 0},
              "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
              "presetId": "simple-launcher",
              "flows": [{"id": "not-a-uuid", "title": "x", "slots": []}],
              "contacts": []
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire) as JsonObject
        val result = ConfigDocumentWireFormat.deserialize(element)
        assertTrue(result is Outcome.Failure, "Expected Failure for malformed UUID, got: $result")
    }

    private fun Outcome<ConfigDocument, BackendError>.orFail(): ConfigDocument =
        when (this) {
            is Outcome.Success -> value
            is Outcome.Failure -> fail("Expected success, got Failure($error)")
        }
}
