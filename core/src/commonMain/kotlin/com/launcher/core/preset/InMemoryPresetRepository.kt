package com.launcher.core.preset

import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory adapter for tests, dev builds, and Core default before app wires DataStore.
 * Production app must provide a persistent adapter.
 */
class InMemoryPresetRepository(initial: FlowPreset? = null) : PresetRepository {
    private val state = MutableStateFlow(initial)

    override suspend fun getActivePreset(): FlowPreset? = state.value
    override suspend fun setActivePreset(preset: FlowPreset) { state.value = preset }
    override suspend fun clear() { state.value = null }
    override fun observeActivePreset(): StateFlow<FlowPreset?> = state
}
