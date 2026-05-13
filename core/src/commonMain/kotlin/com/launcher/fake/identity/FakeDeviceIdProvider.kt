package com.launcher.fake.identity

import com.launcher.api.identity.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fixed-UUID [DeviceIdProvider] for tests and `mockBackend` flavor (FR-012).
 * Default value mirrors data-model.md §Persistence so test fixtures and
 * deserialised payloads can compare byte-for-byte against on-disk JSON.
 */
class FakeDeviceIdProvider(
    initialId: String = DEFAULT_ID,
) : DeviceIdProvider {

    private val flow = MutableStateFlow(initialId)

    override fun currentDeviceId(): Flow<String> = flow.asStateFlow()

    override suspend fun regenerate() {
        flow.value = "regenerated-${flow.value}"
    }

    /** Test-only override; rotates the id without going through [regenerate]'s
     *  deterministic naming so tests can specify an exact value. */
    fun setId(value: String) {
        flow.value = value
    }

    companion object {
        const val DEFAULT_ID: String = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a"
    }
}
