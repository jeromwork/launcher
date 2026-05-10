package com.launcher.test.fakes

import com.launcher.api.capability.Capability
import com.launcher.api.capability.CapabilityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake of [CapabilityRepository] для domain-level тестов и
 * dev/debug builds (FR-048, CLAUDE.md rule 6 mock-first).
 *
 * Usage:
 * ```
 * val fake = FakeCapabilityRepository(initial = listOf(...))
 * fake.setSnapshot(listOf(...)) // переключить состояние в тесте
 * ```
 */
class FakeCapabilityRepository(
    initial: List<Capability> = emptyList(),
) : CapabilityRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<List<Capability>> = state.asStateFlow()
    override fun snapshot(): List<Capability> = state.value

    /** Test helper: переключить snapshot. Subscribers получат emit. */
    fun setSnapshot(capabilities: List<Capability>) {
        state.value = capabilities
    }
}
