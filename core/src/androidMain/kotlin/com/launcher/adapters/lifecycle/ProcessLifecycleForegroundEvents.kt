package com.launcher.adapters.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.launcher.api.config.ConfigSyncConstants
import com.launcher.api.lifecycle.AppForegroundEvents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter

/**
 * Android [AppForegroundEvents] adapter wrapping `ProcessLifecycleOwner.lifecycle`
 * with manual time-based throttle (spec 008 Phase 7 T092, FR-022 T4,
 * research.md §5).
 *
 * Behavior:
 *  - emits `Unit` on ON_RESUME из `ProcessLifecycleOwner`;
 *  - throttles to at-most-once-per-`RESUMED_TRIGGER_THROTTLE_MS` (2 min);
 *  - rapid screen on/off sequences produce at most 1 emit.
 *
 * Article VI §6 attributes:
 *  - Source: AndroidX `ProcessLifecycleOwner`; system-owned, process-level.
 *  - Frequency: per launcher RESUMED + throttle; bursts collapsed.
 *  - Threading: lifecycle callbacks fire on main thread; we use [trySend]
 *    (non-suspending); downstream wires IO dispatcher.
 *  - Battery cost: ~0 — user-bound; no background work.
 *  - Fallback if absent: T1 FCM, T2 NetworkCallback, T3 WorkManager.
 */
class ProcessLifecycleForegroundEvents(
    private val throttleMs: Long = ConfigSyncConstants.RESUMED_TRIGGER_THROTTLE_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AppForegroundEvents {

    private var lastEmitAtMs: Long = Long.MIN_VALUE

    override val onResume: Flow<Unit> = callbackFlow<Unit> {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                trySend(Unit)
            }
        }
        val owner = ProcessLifecycleOwner.get()
        owner.lifecycle.addObserver(observer)

        awaitClose {
            owner.lifecycle.removeObserver(observer)
        }
    }.filter { _ ->
        // Throttle: emit only if at least throttleMs since last emit. Avoid
        // overflow when lastEmit is the sentinel — first emit always allowed.
        val now = nowMillis()
        val isFirst = lastEmitAtMs == Long.MIN_VALUE
        if (isFirst || now - lastEmitAtMs >= throttleMs) {
            lastEmitAtMs = now
            true
        } else {
            false
        }
    }
}
