package com.launcher.cloud.fake

import com.launcher.cloud.api.CloudAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake. `StateFlow` уже гарантирует distinct-until-changed (INV-5)
 * и initial emission (INV-3). [set] — test-only API для имитации
 * AuthProvider sign-in / sign-out events.
 */
class FakeCloudAvailability(initial: Boolean = false) : CloudAvailability {
    private val state = MutableStateFlow(initial)

    override suspend fun isCloudAvailable(): Boolean = state.value

    override val isCloudAvailableFlow: Flow<Boolean> = state.asStateFlow()

    fun set(value: Boolean) {
        state.value = value
    }
}
