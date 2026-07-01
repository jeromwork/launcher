package com.launcher.api.profile

import com.launcher.api.preset.PresetRef

/**
 * Persistence port for [ProfileStoreState]. Implementations live in adapters
 * (e.g. `PreferencesProfileStore` for Android DataStore).
 */
interface ProfileStore {

    suspend fun load(): ProfileStoreState

    suspend fun save(state: ProfileStoreState)

    suspend fun getActive(): Pair<PresetRef, ProfileData>?

    suspend fun putProfile(ref: PresetRef, data: ProfileData)

    suspend fun setActive(ref: PresetRef?)
}
