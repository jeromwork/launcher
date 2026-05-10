package com.launcher.core.health

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.launcher.api.health.Connectivity
import com.launcher.api.health.Health
import com.launcher.core.diagnostics.RecoveryEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Android-side gatherer of [Health] snapshot data.
 *
 * Subscribes to **5 event sources** (FR-018, all event-driven per NFR-N05 — no
 * polling). Each callback body completes within 10 ms (NFR-012); heavier work
 * (snapshot rebuild + persistence) handed off to background dispatcher by the
 * surrounding repository.
 *
 * | Source | When it fires | What we read |
 * |--------|---------------|--------------|
 * | `ConnectivityManager.NetworkCallback` | network appears/lost/changes capability | active network type → [Connectivity] |
 * | `Settings.System.VOLUME_CHANGED` ContentObserver | user changes any stream volume | [AudioManager.STREAM_RING] level + ringerMode |
 * | `Settings.Global.AIRPLANE_MODE_ON` ContentObserver | airplane mode flips | also affects connectivity, but we let NetworkCallback handle that |
 * | `Intent.ACTION_BATTERY_CHANGED` (sticky) | charging plug events | level, isCharging |
 * | `ProcessLifecycleOwner.RESUMED` | app comes to foreground | `lastSeen` timestamp + force snapshot rebuild as a safety net for missed events |
 *
 * Initial snapshot is built synchronously from sticky broadcast (battery) +
 * one-shot `getActiveNetwork()` query at construction; subsequent updates are
 * pushed by the callbacks above.
 *
 * Threading: callbacks run on system threads (binder for broadcasts, main для
 * lifecycle). All state updates go through atomic `MutableStateFlow` swap —
 * thread-safe. Surrounding repository collects on `Dispatchers.Default`.
 */
class AndroidHealthCollector(
    private val context: Context,
    private val appVersion: String,
    private val scope: CoroutineScope,
    private val logger: RecoveryEventLogger? = null,
) {
    private val applicationContext = context.applicationContext
    private val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val state = MutableStateFlow(initialSnapshot())

    /** Hot flow of current health snapshot. Replays the latest value on subscribe. */
    val snapshots: Flow<Health> = state.asStateFlow()

    fun snapshot(): Health = state.value

    init {
        registerNetworkCallback()
        registerVolumeObserver()
        registerAirplaneObserver()
        registerBatteryReceiver()
        registerLifecycleObserver()
    }

    // -- Initial synchronous read ------------------------------------------

    private fun initialSnapshot(): Health = Health(
        batteryPercent = readBatteryPercent(),
        charging = readCharging(),
        connectivity = readConnectivity(),
        ringerVolumePercent = readRingerVolumePercent(),
        audioStreamMuted = readMuted(),
        lastSeen = System.currentTimeMillis(),
        appVersion = appVersion,
    )

    // -- 5 sources ----------------------------------------------------------

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = enqueueRebuild()
                override fun onLost(network: Network) = enqueueRebuild()
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = enqueueRebuild()
            })
        } catch (t: Throwable) {
            logger?.log(
                RecoveryEventLogger.Category.SystemApiFailure,
                "register_network_callback",
                mapOf("err" to (t.message ?: "unknown").take(40)),
            )
        }
    }

    private fun registerVolumeObserver() {
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) = enqueueRebuild()
        }
        try {
            applicationContext.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI, true, observer,
            )
        } catch (t: Throwable) {
            logger?.log(
                RecoveryEventLogger.Category.SystemApiFailure,
                "register_volume_observer",
                mapOf("err" to (t.message ?: "unknown").take(40)),
            )
        }
    }

    private fun registerAirplaneObserver() {
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) = enqueueRebuild()
        }
        try {
            applicationContext.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), false, observer,
            )
        } catch (t: Throwable) {
            logger?.log(
                RecoveryEventLogger.Category.SystemApiFailure,
                "register_airplane_observer",
                mapOf("err" to (t.message ?: "unknown").take(40)),
            )
        }
    }

    private fun registerBatteryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = enqueueRebuild()
        }
        try {
            // Sticky broadcast — ACTION_BATTERY_CHANGED. NB: receiver onReceive
            // here just enqueues; heavy work happens off-binder-thread.
            @Suppress("UnspecifiedRegisterReceiverFlag")
            applicationContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (t: Throwable) {
            logger?.log(
                RecoveryEventLogger.Category.SystemApiFailure,
                "register_battery_receiver",
                mapOf("err" to (t.message ?: "unknown").take(40)),
            )
        }
    }

    private fun registerLifecycleObserver() {
        // Run on main: ProcessLifecycleOwner.get().lifecycle.addObserver requires main thread.
        mainHandler.post {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        // RESUMED — bump lastSeen + safety-net rebuild for any missed events.
                        enqueueRebuild()
                    }
                })
            } catch (t: Throwable) {
                logger?.log(
                    RecoveryEventLogger.Category.SystemApiFailure,
                    "register_lifecycle_observer",
                    mapOf("err" to (t.message ?: "unknown").take(40)),
                )
            }
        }
    }

    // -- Rebuild (heavy) lives on background dispatcher (NFR-012 ≤10ms onReceive) -

    private fun enqueueRebuild() {
        scope.launch(Dispatchers.Default) {
            state.value = Health(
                batteryPercent = readBatteryPercent(),
                charging = readCharging(),
                connectivity = readConnectivity(),
                ringerVolumePercent = readRingerVolumePercent(),
                audioStreamMuted = readMuted(),
                lastSeen = System.currentTimeMillis(),
                appVersion = appVersion,
            )
        }
    }

    // -- Read helpers (off-main, idempotent) -------------------------------

    private fun readBatteryPercent(): Int = try {
        val intent = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) 0 else ((level.toFloat() / scale) * 100f).roundToInt().coerceIn(0, 100)
    } catch (_: Throwable) { 0 }

    private fun readCharging(): Boolean = try {
        val intent = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    } catch (_: Throwable) { false }

    private fun readConnectivity(): Connectivity = try {
        val active = connectivityManager.activeNetwork ?: return Connectivity.None
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return Connectivity.None
        when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> Connectivity.None
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Connectivity.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Connectivity.Mobile
            else -> Connectivity.None
        }
    } catch (_: Throwable) { Connectivity.None }

    private fun readRingerVolumePercent(): Int = try {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        ((current.toFloat() / max) * 100f).roundToInt().coerceIn(0, 100)
    } catch (_: Throwable) { 0 }

    private fun readMuted(): Boolean = try {
        // Effective: STREAM_RING == 0 OR ringerMode != NORMAL (silent / vibrate).
        val volZero = audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0
        val ringerNotNormal = audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        volZero || ringerNotNormal
    } catch (_: Throwable) { false }
}
