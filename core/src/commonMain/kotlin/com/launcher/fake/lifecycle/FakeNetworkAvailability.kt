package com.launcher.fake.lifecycle

import com.launcher.api.lifecycle.NetworkAvailability
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Programmable [NetworkAvailability] для tests. Call [emit] to simulate a
 * `ConnectivityManager.NetworkCallback.onAvailable` event.
 */
class FakeNetworkAvailability : NetworkAvailability {
    private val _onAvailable = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val onAvailable: SharedFlow<Unit> = _onAvailable.asSharedFlow()

    suspend fun emit() {
        _onAvailable.emit(Unit)
    }
}
