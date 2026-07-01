package com.launcher.api.preset

import kotlinx.serialization.Serializable

const val PRESET_SCHEMA_VERSION: Int = 1

/**
 * Self-contained preset wire format (FR-001).
 *
 * Composite identity = ([uid], [version]). The [PresetRef] view is derived
 * via the [ref] extension.
 */
@Serializable
data class Preset(
    val schemaVersion: Int,
    val uid: String,
    val version: Int,
    val slug: String,
    val label: String,
    val description: String,
    val configs: List<Config> = emptyList(),
    val abstractProfile: AbstractProfile? = null,
    val requiredModules: List<String> = emptyList(),
    val optionalModules: List<String> = emptyList(),
    val pickEnabled: Boolean = true,
)

val Preset.ref: PresetRef
    get() = PresetRef(uid = uid, version = version)
