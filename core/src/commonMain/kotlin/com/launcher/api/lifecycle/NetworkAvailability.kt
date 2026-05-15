package com.launcher.api.lifecycle

import kotlinx.coroutines.flow.Flow

/**
 * Port over OS-driven «network became available» events (spec 008 §FR-022 T2,
 * §SC-002, research.md §7).
 *
 * Article VI §6 attributes:
 *  - Source: Android `ConnectivityManager.NetworkCallback.onAvailable` (real
 *    adapter); programmable [Flow] (fake).
 *  - Frequency: per OS-detected network state transition (offline → online).
 *  - Threading: callback dispatched to Dispatchers.IO in adapter.
 *  - Battery cost: ~0 (system-driven, no polling).
 *  - Fallback if absent: T3 WorkManager (15 min) и T4 RESUMED (2 min) cover.
 *
 * Implementations:
 *  - `ConnectivityManagerNetworkAvailability` (androidMain): registers
 *    NetworkCallback lazily on first subscriber, unregisters when no
 *    subscribers (callbackFlow pattern).
 *  - `FakeNetworkAvailability` (commonTest): programmable MutableSharedFlow.
 */
interface NetworkAvailability {
    /**
     * Hot Flow emitting [Unit] each time OS reports a usable network became
     * available. Emissions are «edges» — no value is the same across emits.
     */
    val onAvailable: Flow<Unit>
}
