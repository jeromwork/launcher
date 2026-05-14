package com.launcher.fake.lifecycle

import com.launcher.api.lifecycle.AppForegroundEvents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Programmable [AppForegroundEvents] для tests. Call [emitResume] to simulate
 * an Activity#onResume transition. Note: this fake does NOT enforce the
 * 2-min throttle — tests pass un-throttled timing — caller asserts на behavior
 * of the consumer, not this fake.
 */
class FakeAppForegroundEvents : AppForegroundEvents {
    private val _onResume = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val onResume: SharedFlow<Unit> = _onResume.asSharedFlow()

    suspend fun emitResume() {
        _onResume.emit(Unit)
    }
}
