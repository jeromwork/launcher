package com.launcher.api.link

import com.launcher.wire.WireVersion

import com.launcher.api.config.ElementId
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.config.SlotKind
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for spec 008 extended StateApplied wire format.
 *
 * Covers FR-031..033 + SC-005:
 * - additive over спека 007 LinkBootstrap;
 * - spec 007 reader gracefully reads spec 008 docs (forward compat в один сторону);
 * - partial apply reasons serialize correctly.
 */
class StateAppliedWireFormatTest {

    private val json = WireFormatJson.json

    @Test
    fun roundtrip_bootstrap_only_no_spec_008_fields() {
        // Bootstrap-only state — спека 007 baseline. Spec 008 reader should
        // produce StateApplied with all spec-008 fields nullified/empty.
        val original = StateApplied(
            appliedAt = 1747166400000L,
            presetId = "simple-launcher",
            fcmToken = "fakeToken123",
            updatedAt = 1747166400000L,
        )
        val wire = StateAppliedWireFormat.serialize(original)
        val parsed = StateAppliedWireFormat.deserialize(wire).orFail()
        assertEquals(original, parsed)
        assertNull(parsed.appliedConfigUpdatedAt)
        assertNull(parsed.flowsApplied)
        assertEquals(emptyList(), parsed.partialApplyReasons)
    }

    @Test
    fun roundtrip_full_with_all_spec_008_fields() {
        val flowId = ElementId("f1111111-1111-4111-8111-111111111111")
        val slotId = ElementId("11111111-1111-4111-8111-111111111111")
        val contactId = ElementId("c1111111-1111-4111-8111-111111111111")
        val original = StateApplied(
            appliedAt = 1747166410000L,
            presetId = "simple-launcher",
            fcmToken = "fakeToken123",
            updatedAt = 1747166410000L,
            appliedConfigUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
            flowsApplied = listOf(
                FlowApplied(
                    id = flowId,
                    title = "Главный",
                    slots = listOf(
                        SlotApplied(id = slotId, kind = SlotKind.Call, appliedSuccessfully = true),
                    ),
                ),
            ),
            contactsApplied = listOf(
                ContactApplied(id = contactId, displayName = "Маша", appliedSuccessfully = true),
            ),
            partialApplyReasons = emptyList(),
        )
        val wire = StateAppliedWireFormat.serialize(original)
        val parsed = StateAppliedWireFormat.deserialize(wire).orFail()
        assertEquals(original, parsed)
    }

    @Test
    fun roundtrip_partial_apply_with_reasons() {
        val original = StateApplied(
            appliedAt = 1747166410000L,
            presetId = "simple-launcher",
            fcmToken = null,
            updatedAt = 1747166410000L,
            appliedConfigUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
            partialApplyReasons = listOf(
                PartialReason.ProviderUnavailable,
                PartialReason.UnknownSlotKind,
            ),
        )
        val wire = StateAppliedWireFormat.serialize(original)
        val parsed = StateAppliedWireFormat.deserialize(wire).orFail()
        assertEquals(2, parsed.partialApplyReasons.size)
        assertTrue(PartialReason.ProviderUnavailable in parsed.partialApplyReasons)
        assertTrue(PartialReason.UnknownSlotKind in parsed.partialApplyReasons)
    }

    @Test
    fun backwardCompat_spec_007_reader_reads_spec_008_doc() {
        // Verify that спека 007's LinkBootstrapWireFormat.deserialize can read
        // a doc written by spec 008 (with appliedConfigUpdatedAt etc.) and
        // produce a valid LinkBootstrap with только its known fields.
        val spec008Doc = StateApplied(
            appliedAt = 1747166410000L,
            presetId = "simple-launcher",
            fcmToken = "tok",
            updatedAt = 1747166410000L,
            appliedConfigUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
            flowsApplied = listOf(
                FlowApplied(
                    id = ElementId("f1111111-1111-4111-8111-111111111111"),
                    title = "X",
                    slots = emptyList(),
                ),
            ),
        )
        val wire = StateAppliedWireFormat.serialize(spec008Doc)

        // Spec 007 reader должен прочесть это без проблем (ignores extra fields).
        val spec007Read = LinkBootstrapWireFormat.deserialize(wire)
        assertTrue(spec007Read is Outcome.Success, "Spec 007 reader должен прочесть spec 008 docs")
        val bootstrap = (spec007Read as Outcome.Success).value
        assertEquals("simple-launcher", bootstrap.presetId)
        assertEquals("tok", bootstrap.fcmToken)
    }

    @Test
    fun unknown_partial_reason_silently_dropped() {
        // Forward-compat: future PartialReason value reaching old reader
        // should be silently dropped (not crash). Other reasons preserved.
        val wire = json.parseToJsonElement("""
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "appliedAt": 1747166410000,
              "presetId": "simple-launcher",
              "updatedAt": 1747166410000,
              "partialApplyReasons": ["ProviderUnavailable", "FutureUnknownReason"]
            }
        """.trimIndent()) as JsonObject
        val parsed = StateAppliedWireFormat.deserialize(wire).orFail()
        assertEquals(1, parsed.partialApplyReasons.size)
        assertEquals(PartialReason.ProviderUnavailable, parsed.partialApplyReasons.first())
    }

    @Test
    fun appliedConfigUpdatedAt_omitted_when_null() {
        val state = StateApplied(
            appliedAt = 1L,
            presetId = "x",
            fcmToken = null,
            updatedAt = 1L,
            appliedConfigUpdatedAt = null,
        )
        val wire = StateAppliedWireFormat.serialize(state)
        assertTrue(
            !wire.containsKey("appliedConfigUpdatedAt"),
            "null appliedConfigUpdatedAt should be omitted, got: $wire",
        )
    }

    @Test
    fun future_schema_version_rejected() {
        val wire = json.parseToJsonElement("""
            {"schemaVersion": "999.0", "minReaderVersion": "999.0", "minWriterVersion": "999.0", "appliedAt": 1, "presetId": "x", "updatedAt": 1}
        """.trimIndent()) as JsonObject
        val result = StateAppliedWireFormat.deserialize(wire)
        assertTrue(result is Outcome.Failure)
    }

    private fun Outcome<StateApplied, BackendError>.orFail(): StateApplied =
        when (this) {
            is Outcome.Success -> value
            is Outcome.Failure -> fail("Expected success, got Failure($error)")
        }
}
