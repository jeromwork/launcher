package com.launcher.test

import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.action.ProviderState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory test double for [ProviderRegistry]. Lets tests programmatically
 * pin per-provider availability and emit fresh snapshots through [updates].
 *
 * Per CLAUDE.md §6 (mock-first).
 */
class FakeProviderRegistry(
    initial: List<ProviderState> = emptyList(),
) : ProviderRegistry {

    private val state: MutableStateFlow<List<ProviderState>> = MutableStateFlow(initial)

    override val updates: Flow<List<ProviderState>> = state.asStateFlow()

    override fun snapshot(): List<ProviderState> = state.value

    override fun availability(providerId: ProviderId): ProviderAvailability =
        state.value
            .firstOrNull { it.providerId == providerId }
            ?.availability
            ?: ProviderAvailability.Missing(installHint = null)

    /** Replace the snapshot; also drives [updates] emission. */
    fun setSnapshot(newSnapshot: List<ProviderState>) {
        state.value = newSnapshot
    }

    /** Replace availability for one provider; preserves others. */
    fun setAvailability(providerId: ProviderId, availability: ProviderAvailability) {
        val current = state.value.toMutableList()
        val idx = current.indexOfFirst { it.providerId == providerId }
        val newState = ProviderState(providerId = providerId, availability = availability)
        if (idx >= 0) current[idx] = newState else current.add(newState)
        state.value = current
    }
}
