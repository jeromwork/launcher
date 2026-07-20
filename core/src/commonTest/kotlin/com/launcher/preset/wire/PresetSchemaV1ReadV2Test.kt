package com.launcher.preset.wire

import com.launcher.wire.WireVersion

import com.launcher.preset.model.Preset
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T022 (FR-014, contracts/preset-schema-v2.md, CLAUDE.md rule 5).
 *
 * Backward-compat:
 *   - v2 reader on v1 JSON (no `hintFlow`, no `wizardPresentation`) → both default to null.
 *   - v1 reader on v2 JSON: kotlinx.serialization ignores unknown keys (`ignoreUnknownKeys`),
 *     so a v1 model reading a v2 blob simply drops the additions.
 */
class PresetSchemaV1ReadV2Test {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun v2Reader_onV1Json_defaultsHintFlowAndWizardPresentationToNull() {
        // Hard-coded v1 wire format — no `hintFlow`, no `wizardPresentation`.
        val v1Json = """
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "presetId": "legacy-preset",
              "version": 1,
              "layoutKey": "simple",
              "wizardFlow": [],
              "settingsMap": [],
              "activeComponents": []
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Preset.serializer(), v1Json)

        assertEquals(WireVersion(1, 0), decoded.schemaVersion)
        assertEquals("legacy-preset", decoded.presetId)
        assertNull(decoded.hintFlow, "v2 model must default hintFlow to null on v1 JSON")
        assertNull(decoded.wizardPresentation, "v2 model must default wizardPresentation to null on v1 JSON")
    }

    @Test
    fun v2Reader_onV2Json_withoutOptionalFields_defaultsToNull() {
        // v2 blob that omits the two v2-only optional fields — still valid.
        val v2JsonNoOpts = """
            {
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "presetId": "empty-v2",
              "version": 1,
              "layoutKey": "simple",
              "wizardFlow": [],
              "settingsMap": [],
              "activeComponents": []
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Preset.serializer(), v2JsonNoOpts)

        assertEquals(WireVersion(2, 0), decoded.schemaVersion)
        assertNull(decoded.hintFlow)
        assertNull(decoded.wizardPresentation)
    }

    @Test
    fun v1ReaderStyle_onV2Json_ignoresHintFlowAndWizardPresentation() {
        // Simulates a v1 reader: uses ignoreUnknownKeys and only reads v1 fields.
        // Since our Preset model IS the v2 model, this proves the forward compat
        // contract: unknown fields are skipped rather than throwing.
        val v2JsonWithExtras = """
            {
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "presetId": "future-preset",
              "version": 1,
              "layoutKey": "simple",
              "wizardFlow": [],
              "settingsMap": [],
              "activeComponents": [],
              "hintFlow": [
                {"hintId":"h1","targetComponentId":"c1","textKey":"k1"}
              ],
              "wizardPresentation": {"darkMode": true, "typographyScale": "ExtraLarge"},
              "someHypotheticalV3Field": {"nested": true}
            }
        """.trimIndent()

        // Model decodes cleanly and unknown "someHypotheticalV3Field" is skipped.
        val decoded = json.decodeFromString(Preset.serializer(), v2JsonWithExtras)
        assertEquals("future-preset", decoded.presetId)
        assertEquals(1, decoded.hintFlow?.size)
    }
}
