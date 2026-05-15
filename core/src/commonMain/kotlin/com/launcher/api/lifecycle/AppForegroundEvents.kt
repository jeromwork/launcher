package com.launcher.api.lifecycle

import kotlinx.coroutines.flow.Flow

/**
 * Port over «app became foreground» events, throttled per FR-022 T4 (2 min,
 * see [com.launcher.api.config.ConfigSyncConstants.RESUMED_TRIGGER_THROTTLE_MS]).
 *
 * Article VI §6 attributes:
 *  - Source: Android `ProcessLifecycleOwner.lifecycle` (real adapter);
 *    programmable [Flow] (fake).
 *  - Frequency: per RESUMED transition + throttle 2 min — rapid screen
 *    on/off cycles produce at most 1 emit per 2 min.
 *  - Threading: collected on main; dispatched to IO для fetch.
 *  - Battery cost: ~0 (user-bound, no background).
 *  - Fallback if absent: T1 FCM, T2 NetworkCallback, T3 WorkManager.
 *
 * Implementations:
 *  - `ProcessLifecycleForegroundEvents` (androidMain): subscribes to
 *    ProcessLifecycleOwner.lifecycle, throttles via `sample`-style operator.
 *  - `FakeAppForegroundEvents` (commonTest): programmable MutableSharedFlow.
 */
interface AppForegroundEvents {
    /**
     * Hot Flow emitting [Unit] when launcher Activity becomes RESUMED, throttled
     * to at most once per [com.launcher.api.config.ConfigSyncConstants.RESUMED_TRIGGER_THROTTLE_MS].
     */
    val onResume: Flow<Unit>
}
