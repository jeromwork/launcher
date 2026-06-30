package com.launcher.api.profile

import kotlinx.serialization.Serializable

/**
 * Per-preset live state stored on the device. Persisted as a value inside
 * [ProfileStoreState.profiles] keyed by [com.launcher.api.preset.PresetRef]
 * composite-key.
 *
 * Clarification #9 hook: [unassigned] reserved for future "extra slots" UI.
 */
@Serializable
data class ProfileData(
    val layout: Layout,
    val bindings: List<Binding> = emptyList(),
    val settings: List<SettingEntry> = emptyList(),
    val unassigned: List<Binding> = emptyList(),
)
