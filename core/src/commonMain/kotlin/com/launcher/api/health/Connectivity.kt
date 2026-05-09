package com.launcher.api.health

import kotlinx.serialization.Serializable

/**
 * Network reachability classification used by [Health.connectivity].
 *
 * Reserved for future minor bumps: `Vpn`, `Ethernet`. Adding values is
 * non-breaking — readers using [fromWireOrNone] survive unknown enum names
 * by mapping them to [None] (FR-044).
 */
@Serializable
enum class Connectivity {
    Wifi,
    Mobile,
    None;

    companion object {
        /**
         * Safe parser for wire-format strings. Unknown / null values map to [None]
         * per FR-044 — better to under-report network availability than to crash.
         */
        fun fromWireOrNone(name: String?): Connectivity =
            entries.firstOrNull { it.name == name } ?: None
    }
}
