package com.launcher.api.settings

import family.wire.WireVersion
import com.launcher.api.wireformat.WireFormatJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wire-format tests for [LauncherSettings] per
 * [`contracts/launcher-settings-wire-format.md`](specs/006-provider-capabilities-and-health/contracts/launcher-settings-wire-format.md)
 * v1.0.0. Covers:
 *  - Roundtrip всех 4 комбинаций banner toggles.
 *  - `defaultsForPreset(slug)` matches expected JSON для каждого preset (FR-051).
 *  - Forward-compat: schemaVersion > SUPPORTED + reserved fields (`banners.offline`,
 *    `raiseRingerOnLongOffline`, `escalation.*`) parses without crash (SC-015).
 */
class LauncherSettingsWireFormatTest {

    private val json = WireFormatJson.json

    private fun roundtrip(s: LauncherSettings): LauncherSettings =
        json.decodeFromString(LauncherSettings.serializer(), json.encodeToString(LauncherSettings.serializer(), s))

    // -- Roundtrip: 4 banner toggle combinations ---------------------------

    @Test
    fun roundtrip_bothOff() {
        val original = LauncherSettings(banners = BannerToggles(airplane = false, mute = false))
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_airplaneOnly() {
        val original = LauncherSettings(banners = BannerToggles(airplane = true, mute = false))
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_muteOnly() {
        val original = LauncherSettings(banners = BannerToggles(airplane = false, mute = true))
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_bothOn() {
        val original = LauncherSettings(banners = BannerToggles(airplane = true, mute = true))
        assertEquals(original, roundtrip(original))
    }

    // -- defaultsForPreset (FR-051) ----------------------------------------

    @Test
    fun defaultsForPreset_simpleLauncher_bothOn() {
        val defaults = LauncherSettings.defaultsForPreset("simple-launcher")
        assertTrue(defaults.banners.airplane, "senior preset must enable airplane banner by default")
        assertTrue(defaults.banners.mute, "senior preset must enable mute banner by default")
        assertEquals(WireVersion.parse("1.0"), defaults.schemaVersion)
    }

    @Test
    fun defaultsForPreset_workspace_bothOff() {
        val defaults = LauncherSettings.defaultsForPreset("workspace")
        assertFalse(defaults.banners.airplane)
        assertFalse(defaults.banners.mute)
    }

    @Test
    fun defaultsForPreset_launcher_bothOff() {
        val defaults = LauncherSettings.defaultsForPreset("launcher")
        assertFalse(defaults.banners.airplane)
        assertFalse(defaults.banners.mute)
    }

    @Test
    fun defaultsForPreset_unknownSlug_bothOff() {
        // Future preset: opt-in by default (safer than opt-out for non-senior audiences).
        val defaults = LauncherSettings.defaultsForPreset("future-preset")
        assertFalse(defaults.banners.airplane)
        assertFalse(defaults.banners.mute)
    }

    // -- Forward-compat (FR-043, SC-015) -----------------------------------

    @Test
    fun futureSchemaVersion_withReservedFields_parses() {
        // Reserved field names from contract — спек 013 & 008 will populate
        // these without bumping schemaVersion. Spec 006 reader ignores them.
        val wire = """{
            "schemaVersion": "999.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
            "banners": {
                "airplane": true,
                "mute": false,
                "offline": true
            },
            "raiseRingerOnLongOffline": true,
            "escalation": {
                "firstStepMinutes": 60,
                "subsequentStepMinutes": 30,
                "stepPercent": 20
            }
        }"""
        val parsed = json.decodeFromString(LauncherSettings.serializer(), wire)
        assertEquals(WireVersion.parse("999.0"), parsed.schemaVersion)
        assertTrue(parsed.banners.airplane)
        assertFalse(parsed.banners.mute)
        // banners.offline ignored — not in v1 BannerToggles.
    }

    // -- Defaults policy (FR-042): missing fields use @Serializable defaults ---

    @Test
    fun missingBannersField_usesDefault() {
        val wire = """{"schemaVersion":"1.0","minReaderVersion":"1.0","minWriterVersion":"1.0"}"""
        val parsed = json.decodeFromString(LauncherSettings.serializer(), wire)
        // BannerToggles default ctor → airplane=false, mute=false
        assertFalse(parsed.banners.airplane)
        assertFalse(parsed.banners.mute)
    }

    @Test
    fun missingTogglesInBanners_useDefaults() {
        val wire = """{"schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0","banners":{}}"""
        val parsed = json.decodeFromString(LauncherSettings.serializer(), wire)
        assertFalse(parsed.banners.airplane)
        assertFalse(parsed.banners.mute)
    }

    @Test
    fun missingSchemaVersion_usesDefault() {
        val wire = """{"banners":{"airplane":true,"mute":true}}"""
        val parsed = json.decodeFromString(LauncherSettings.serializer(), wire)
        assertEquals(WireVersion.parse("1.0"), parsed.schemaVersion)
    }
}
