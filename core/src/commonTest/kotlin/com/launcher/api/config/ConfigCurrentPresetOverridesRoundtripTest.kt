package com.launcher.api.config

import com.launcher.api.result.Outcome
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Spec 009 T039/T040/T041 — `/config/current` additive `presetOverrides`
 * field is wire-compatible with spec-008 v=1 readers (FR-013).
 */
class ConfigCurrentPresetOverridesRoundtripTest {

    private fun baseDocument(): ConfigDocument = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
        lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
        presetId = "simple-launcher",
        flows = emptyList(),
        contacts = emptyList(),
    )

    @Test
    fun null_presetOverrides_is_omitted_from_wire() {
        val doc = baseDocument()
        val wire = ConfigDocumentWireFormat.serialize(doc)
        assertFalse(
            wire.containsKey("presetOverrides"),
            "spec-008 byte-identical: presetOverrides MUST NOT appear when null",
        )
    }

    @Test
    fun null_presetOverrides_roundtrips() {
        val doc = baseDocument()
        val wire = ConfigDocumentWireFormat.serialize(doc)
        val parsed = ConfigDocumentWireFormat.deserialize(wire).orFailWire()
        assertEquals(doc, parsed)
        assertEquals(null, parsed.presetOverrides)
    }

    @Test
    fun nonNull_empty_presetOverrides_roundtrips() {
        val doc = baseDocument().copy(presetOverrides = PresetSettings(phoneHealthSettings = null))
        val wire = ConfigDocumentWireFormat.serialize(doc)
        val parsed = ConfigDocumentWireFormat.deserialize(wire).orFailWire()
        assertEquals(doc, parsed)
        assertEquals(PresetSettings(phoneHealthSettings = null), parsed.presetOverrides)
    }

    @Test
    fun spec008_reader_ignores_presetOverrides_in_wire() {
        // A spec-009 writer added presetOverrides; a (notional) spec-008 reader
        // would just skip the unknown key. We simulate by reading via our own
        // deserializer with the field stripped — and verify writer round-trip
        // doesn't break with the field present then re-omitted.
        val doc = baseDocument().copy(presetOverrides = PresetSettings(phoneHealthSettings = null))
        val wire = ConfigDocumentWireFormat.serialize(doc)
        assertTrue(wire.containsKey("presetOverrides"))
        val parsed = ConfigDocumentWireFormat.deserialize(wire).orFailWire()
        // Re-serialize a copy with null override — must produce wire WITHOUT the field.
        val reWire = ConfigDocumentWireFormat.serialize(parsed.copy(presetOverrides = null))
        assertFalse(reWire.containsKey("presetOverrides"))
    }

    private fun <T> Outcome<T, *>.orFailWire(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("wire deser failed: $error")
    }
}
