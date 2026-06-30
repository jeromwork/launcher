package com.launcher.api.profile

import com.launcher.api.preset.PresetRef
import kotlinx.serialization.Serializable

const val PROFILE_STORE_SCHEMA_VERSION: Int = 1

/**
 * Top-level persisted state.
 *
 * Map key is [PresetRef.toCompositeKey] = `"<uid>::<version>"` (decision R3).
 * Serializable as `Map<String, ProfileData>` because JSON Map keys must be
 * strings — typed access goes through [PresetRef.parseCompositeKey] in store.
 */
@Serializable
data class ProfileStoreState(
    val schemaVersion: Int = PROFILE_STORE_SCHEMA_VERSION,
    val activePresetRef: PresetRef? = null,
    val profiles: Map<String, ProfileData> = emptyMap(),
)
