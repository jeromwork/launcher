package com.launcher.adapters.lifecycle

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.launcher.api.lifecycle.NetworkAvailability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android [NetworkAvailability] adapter wrapping
 * `ConnectivityManager.NetworkCallback.onAvailable` (spec 008 Phase 7 T090,
 * FR-022 T2, research.md §7).
 *
 * Behavior:
 *  - `callbackFlow` registers a fresh callback on first subscribe;
 *  - emits `Unit` only on `onAvailable` (offline → online transitions; new
 *    network instance becoming usable);
 *  - unregisters callback on cancellation (no leaked callbacks).
 *
 * Article VI §6 attributes:
 *  - Source: Android `ConnectivityManager`; system-owned.
 *  - Frequency: per genuine connectivity transition (WiFi connect, mobile
 *    handoff). Typically 0-10/hour for stable users; bursts possible during
 *    travel.
 *  - Threading: callback dispatched на ConnectivityManager's default executor
 *    (not main thread); consumers wire downstream IO dispatcher.
 *  - Battery cost: ~0 — passive system event.
 *  - Fallback if absent: T3 WorkManager (15 min) and T4 RESUMED (2 min throttle)
 *    cover the gap.
 *
 * Konsist gate: `android.net.*` imports stay in this file; commonMain port
 * type `NetworkAvailability` exposes only [Flow] of [Unit].
 */
class ConnectivityManagerNetworkAvailability(
    context: Context,
) : NetworkAvailability {

    private val appContext = context.applicationContext
    private val connectivityManager: ConnectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val onAvailable: Flow<Unit> = callbackFlow {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Best-effort emit; subscriber may be slow — buffer overflow is
                // OK (we only care «something happened»).
                trySend(Unit)
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Throwable) {
                // Already unregistered; ignore.
            }
        }
    }
}
