package com.launcher.test.fitness

import com.launcher.api.action.ProviderId
import com.launcher.api.capability.Capability
import com.launcher.api.health.Connectivity
import com.launcher.api.health.Health
import com.launcher.api.settings.BannerToggles
import com.launcher.api.settings.LauncherSettings
import com.launcher.api.wireformat.WireFormatJson
import org.junit.Test
import kotlin.test.assertFalse

/**
 * Fitness test for spec 006 FR-012 / SC-012: wire-format JSON outputs MUST NOT
 * contain `Base64` / `base64` substrings. Catches accidental introduction of
 * binary blobs into the wire format (icons, photos, payloads).
 *
 * Roundtrip 3 representative samples + assert. If a future change adds a
 * field that serialises bytes as base64 (e.g. `iconBytes: ByteArray` would
 * trigger this), the test fails immediately.
 */
class Spec006NoBase64Test {

    private val json = WireFormatJson.json

    @Test
    fun capability_serialised_does_not_contain_base64() {
        val capability = Capability(
            providerId = ProviderId.WHATSAPP,
            displayName = "WhatsApp",
            iconId = "bundled:whatsapp",
            iconSha256 = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            available = true,
            versionCode = 241800L,
        )
        val wire = json.encodeToString(Capability.serializer(), capability)
        assertNoBase64(wire, "Capability")
    }

    @Test
    fun health_serialised_does_not_contain_base64() {
        val health = Health(
            batteryPercent = 80,
            charging = false,
            connectivity = Connectivity.Wifi,
            ringerVolumePercent = 50,
            audioStreamMuted = false,
            lastSeen = 1746780123456L,
            appVersion = "1.4.2",
        )
        val wire = json.encodeToString(Health.serializer(), health)
        assertNoBase64(wire, "Health")
    }

    @Test
    fun launcherSettings_serialised_does_not_contain_base64() {
        val settings = LauncherSettings(
            banners = BannerToggles(airplane = true, mute = true),
        )
        val wire = json.encodeToString(LauncherSettings.serializer(), settings)
        assertNoBase64(wire, "LauncherSettings")
    }

    private fun assertNoBase64(wire: String, type: String) {
        assertFalse(
            wire.contains("base64", ignoreCase = true),
            "$type wire-format MUST NOT contain 'base64' substring (FR-012). Got: $wire",
        )
    }
}
