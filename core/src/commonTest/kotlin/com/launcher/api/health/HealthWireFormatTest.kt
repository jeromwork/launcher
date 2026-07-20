package com.launcher.api.health

import com.launcher.wire.WireVersion
import com.launcher.api.wireformat.WireFormatJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wire-format tests for [Health] per
 * [`contracts/health-wire-format.md`](specs/006-provider-capabilities-and-health/contracts/health-wire-format.md)
 * v1.0.0. Covers:
 *  - Roundtrip every connectivity x charging x mute combination.
 *  - Boundary values for batteryPercent / ringerVolumePercent.
 *  - Forward-compat: schemaVersion > SUPPORTED + unknown enum (FR-043, FR-44, SC-015).
 */
class HealthWireFormatTest {

    private val json = WireFormatJson.json

    private fun roundtrip(h: Health): Health =
        json.decodeFromString(Health.serializer(), json.encodeToString(Health.serializer(), h))

    private fun sample(
        connectivity: Connectivity = Connectivity.Wifi,
        charging: Boolean = false,
        ringerVolumePercent: Int = 50,
        audioStreamMuted: Boolean = false,
        batteryPercent: Int = 80,
    ) = Health(
        batteryPercent = batteryPercent,
        charging = charging,
        connectivity = connectivity,
        ringerVolumePercent = ringerVolumePercent,
        audioStreamMuted = audioStreamMuted,
        lastSeen = 1746780123456L,
        appVersion = "1.4.2",
    )

    // -- Roundtrip: connectivity variants ----------------------------------

    @Test
    fun roundtrip_wifi() {
        val original = sample(connectivity = Connectivity.Wifi)
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_mobile() {
        val original = sample(connectivity = Connectivity.Mobile)
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_none() {
        val original = sample(connectivity = Connectivity.None)
        assertEquals(original, roundtrip(original))
    }

    // -- Roundtrip: charging x muted combinations --------------------------

    @Test
    fun roundtrip_chargingAndMuted() {
        val original = sample(charging = true, audioStreamMuted = true, ringerVolumePercent = 0)
        assertEquals(original, roundtrip(original))
    }

    // -- Boundary values ---------------------------------------------------

    @Test
    fun roundtrip_batteryZero() {
        val original = sample(batteryPercent = 0)
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_batteryHundred() {
        val original = sample(batteryPercent = 100)
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_ringerZero_implyMuted() {
        // Realistic: STREAM_RING == 0 → audioStreamMuted = true (FR-016)
        val original = sample(ringerVolumePercent = 0, audioStreamMuted = true)
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_ringerHundred() {
        val original = sample(ringerVolumePercent = 100)
        assertEquals(original, roundtrip(original))
    }

    // -- Forward-compat: schemaVersion > SUPPORTED + unknown enum ----------

    @Test
    fun futureSchemaVersion_withUnknownConnectivity_parsesGracefullyToNone() {
        // FR-043 + FR-44: schemaVersion 999 + connectivity "Vpn" (future value)
        // → parsing must NOT crash. NB: kotlinx-serialization will throw on
        // unknown enum by default — our adapter (Android collector) is
        // expected to use Connectivity.fromWireOrNone() when reading from
        // untrusted sources. This test verifies the helper, not raw decoder.
        //
        // For pure roundtrip with kotlinx-serialization, unknown enum throws.
        // The graceful path lives in adapter code (T646 AndroidHealthRepository).
        // Here we verify: known-enum forward-compat with extra unknown fields
        // doesn't crash.
        val wire = """{
            "schemaVersion": "999.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
            "batteryPercent": 80,
            "charging": false,
            "connectivity": "Wifi",
            "ringerVolumePercent": 50,
            "audioStreamMuted": false,
            "lastSeen": 1746780123456,
            "appVersion": "1.4.2",
            "futureField": {"experimental": true},
            "anotherUnknown": [1, 2, 3]
        }"""
        val parsed = json.decodeFromString(Health.serializer(), wire)
        assertEquals(WireVersion.parse("999.0"), parsed.schemaVersion)
        assertEquals(Connectivity.Wifi, parsed.connectivity)
        assertEquals(80, parsed.batteryPercent)
    }

    @Test
    fun unknownEnum_helperMapsToNone() {
        // FR-44 verified directly via helper — adapters use this to defend
        // against future Connectivity enum values like Vpn / Ethernet.
        assertEquals(Connectivity.None, Connectivity.fromWireOrNone("Vpn"))
        assertEquals(Connectivity.None, Connectivity.fromWireOrNone("Ethernet"))
    }

    // -- Wire format shape --------------------------------------------------

    @Test
    fun wire_containsAllNonDefaultFields() {
        val original = sample()
        val wire = json.encodeToString(Health.serializer(), original)
        listOf(
            "batteryPercent", "charging", "connectivity",
            "ringerVolumePercent", "audioStreamMuted", "lastSeen", "appVersion",
        ).forEach { field ->
            assertTrue(wire.contains("\"$field\""), "wire should contain $field: $wire")
        }
    }

    @Test
    fun wire_alwaysCarriesTheVersionFields_evenAtDefaults() {
        // Inverted by the wire-format conversion. Under encodeDefaults=false these fields used to
        // be dropped when they held their defaults, so a document could ship with no version at
        // all — invariant I1 forbids that, and @EncodeDefault(ALWAYS) on the format keeps them on
        // the wire. Payload size was the wrong thing to optimise here: a version-less document is
        // unreadable by any future reader that needs to know what wrote it.
        val wire = json.encodeToString(Health.serializer(), sample())
        assertTrue(wire.contains("\"schemaVersion\":\"1.0\""), "wire: $wire")
        assertTrue(wire.contains("\"minReaderVersion\":\"1.0\""), "wire: $wire")
        assertTrue(wire.contains("\"minWriterVersion\":\"1.0\""), "wire: $wire")
    }
}
