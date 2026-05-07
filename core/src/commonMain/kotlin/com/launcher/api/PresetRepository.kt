package com.launcher.api

import kotlinx.coroutines.flow.Flow

/**
 * Port: persistence of the user-selected preset.
 * Returns null until the user has picked one (drives first-launch picker).
 */
interface PresetRepository {
    suspend fun getActivePreset(): FlowPreset?
    suspend fun setActivePreset(preset: FlowPreset)
    suspend fun clear()
    fun observeActivePreset(): Flow<FlowPreset?>
}
