package com.launcher.preset.wire

import com.launcher.wire.WireVersion

import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.HintFlowEntry
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Sensitivity
import com.launcher.preset.model.SettingsMapEntry
import com.launcher.preset.model.TypographyScale
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.model.WizardFlowEntry
import com.launcher.preset.model.WizardPresentation
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T021 (FR-014, contracts/preset-schema-v2.md, CLAUDE.md rule 5).
 *
 * Roundtrip: encode Preset with `hintFlow` + `wizardPresentation` populated → decode
 * → assertEquals. Guards the v2 wire format.
 */
class PresetSchemaV2RoundtripTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun v2Preset_withHintFlowAndWizardPresentation_roundtrips() {
        val preset = Preset(
            schemaVersion = WireVersion(2, 0),
            presetId = "simple-launcher",
            version = 2,
            layoutKey = "simple",
            wizardFlow = listOf(
                WizardFlowEntry(
                    poolRef = "launcher-role",
                    order = 1,
                    wizardTitleKey = "step_launcher_role_title",
                    behavior = WizardBehavior.AutoApply,
                ),
                WizardFlowEntry(
                    poolRef = "font-size-large",
                    order = 2,
                    wizardTitleKey = "step_font_size_title",
                    behavior = WizardBehavior.Interactive,
                ),
            ),
            settingsMap = listOf(
                SettingsMapEntry(
                    poolRef = "font-size-large",
                    categoryKey = "settings_display",
                    sensitivity = Sensitivity.Normal,
                ),
            ),
            activeComponents = listOf(
                ActiveComponentEntry(poolRef = "launcher-role"),
            ),
            hintFlow = listOf(
                HintFlowEntry(
                    hintId = "hint-launcher-role",
                    targetComponentId = "launcher-role",
                    textKey = "hint_launcher_role_body",
                ),
            ),
            wizardPresentation = WizardPresentation(
                darkMode = false,
                typographyScale = TypographyScale.Large,
            ),
        )

        val encoded = json.encodeToString(Preset.serializer(), preset)
        val decoded = json.decodeFromString(Preset.serializer(), encoded)

        assertEquals(preset, decoded)
        assertEquals(WireVersion(2, 0), decoded.schemaVersion)
        assertEquals(1, decoded.hintFlow?.size)
        assertEquals("hint-launcher-role", decoded.hintFlow?.first()?.hintId)
        assertEquals(TypographyScale.Large, decoded.wizardPresentation?.typographyScale)

        // Bit-identical re-encode (CLAUDE.md rule 5 stability guard).
        val reencoded = json.encodeToString(Preset.serializer(), decoded)
        assertEquals(encoded, reencoded)
    }
}
