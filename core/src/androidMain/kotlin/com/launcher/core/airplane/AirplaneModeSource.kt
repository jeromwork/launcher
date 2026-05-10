package com.launcher.core.airplane

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.launcher.core.diagnostics.RecoveryEventLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Android-side source of `Flow<Boolean>` reflecting `Settings.Global.AIRPLANE_MODE_ON`.
 * Wired into [com.launcher.api.alerts.AlertBannerStateProvider] via DI as the
 * `airplaneMode` parameter — keeps `commonMain` free of `Settings.Global`
 * Android types per FR-047.
 *
 * Implementation: ContentObserver on `Settings.Global.AIRPLANE_MODE_ON` URI.
 * Uses [callbackFlow] to bridge observer callbacks → cold flow.
 *
 * Initial value читается synchronously when subscribers attach — никаких
 * "no value yet" emissions.
 */
class AirplaneModeSource(
    private val context: Context,
    private val logger: RecoveryEventLogger? = null,
) {
    private val applicationContext = context.applicationContext

    val flow: Flow<Boolean> = callbackFlow {
        // Initial emission.
        trySend(read())

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(read())
            }
        }
        try {
            applicationContext.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), false, observer,
            )
        } catch (t: Throwable) {
            logger?.log(
                RecoveryEventLogger.Category.SystemApiFailure,
                "register_airplane_source",
                mapOf("err" to (t.message ?: "unknown").take(40)),
            )
        }
        awaitClose {
            try {
                applicationContext.contentResolver.unregisterContentObserver(observer)
            } catch (_: Throwable) { /* ignore — observer may already be detached */ }
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private fun read(): Boolean = try {
        Settings.Global.getInt(applicationContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (_: Throwable) { false }
}
