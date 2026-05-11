package com.launcher.api.health

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [Connectivity.fromWireOrNone] safe parser per FR-44.
 *
 * Unknown enum values MUST map to [Connectivity.None] — better to under-report
 * network availability than to crash a reader on a future schema with new
 * connectivity types (e.g. `Vpn`, `Ethernet`).
 */
class ConnectivityTest {

    @Test
    fun fromWire_wifi() {
        assertEquals(Connectivity.Wifi, Connectivity.fromWireOrNone("Wifi"))
    }

    @Test
    fun fromWire_mobile() {
        assertEquals(Connectivity.Mobile, Connectivity.fromWireOrNone("Mobile"))
    }

    @Test
    fun fromWire_none() {
        assertEquals(Connectivity.None, Connectivity.fromWireOrNone("None"))
    }

    @Test
    fun fromWire_null_mapsToNone() {
        assertEquals(Connectivity.None, Connectivity.fromWireOrNone(null))
    }

    @Test
    fun fromWire_emptyString_mapsToNone() {
        assertEquals(Connectivity.None, Connectivity.fromWireOrNone(""))
    }

    @Test
    fun fromWire_unknownFutureValue_mapsToNone() {
        // Anticipated в schema v2: Vpn, Ethernet. Spec 006 reader survives.
        assertEquals(Connectivity.None, Connectivity.fromWireOrNone("Vpn"))
        assertEquals(Connectivity.None, Connectivity.fromWireOrNone("Ethernet"))
    }

    @Test
    fun fromWire_lowercase_mapsToNone() {
        // Enum names are case-sensitive in Kotlin — лowercase doesn't match.
        // FR-44 fallback engages: maps to None.
        assertEquals(Connectivity.None, Connectivity.fromWireOrNone("wifi"))
    }
}
