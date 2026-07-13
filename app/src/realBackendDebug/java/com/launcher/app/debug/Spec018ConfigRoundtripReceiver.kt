package com.launcher.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cryptokit.keys.api.ConfigSaver
import cryptokit.keys.api.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Spec 018 (F-5b) round-trip debug receiver. **realBackendDebug only** —
 * не попадает в release APK.
 *
 * Триггер из ADB:
 *
 *   adb shell am broadcast -n com.launcher.app/.debug.Spec018ConfigRoundtripReceiver \
 *     -a com.launcher.app.DEBUG_F5B_ROUNDTRIP
 *
 * Что делает:
 *   1. Генерирует payload "F5B-RT-{timestamp}".getBytes().
 *   2. configSaver.saveOwn("debug-rt", payload).
 *   3. configSaver.loadOwn("debug-rt").
 *   4. Сравнивает байт-в-байт.
 *   5. Логирует в TAG=F5bRoundtrip результат + content для verify в logcat.
 *
 * Цель — закрытие приёмки spec 018: «один пользователь сохранил → расшифровал
 * с сервера → byte-equal на одном устройстве».
 */
class Spec018ConfigRoundtripReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "round-trip START")
        val saver = runCatching {
            GlobalContext.get().get<ConfigSaver>()
        }.getOrNull()
        if (saver == null) {
            Log.e(TAG, "ConfigSaver not wired in Koin")
            return
        }

        val payload = "F5B-RT-${System.currentTimeMillis()}".toByteArray()
        Log.i(TAG, "payload sent: ${payload.toString(Charsets.UTF_8)} (len=${payload.size})")

        scope.launch {
            // Step 1: save
            when (val r = saver.saveOwn(CONFIG_NAME, payload)) {
                is Outcome.Success -> Log.i(TAG, "saveOwn OK")
                is Outcome.Failure -> {
                    Log.e(TAG, "saveOwn FAILED: ${r.error}")
                    return@launch
                }
            }

            // Step 2: load (читаем с сервера, не из кэша — LocalFirstConfigSaver
            // должен прозрачно расшифровать через EnvelopeRemoteStorage).
            when (val r = saver.loadOwn(CONFIG_NAME)) {
                is Outcome.Success -> {
                    val loaded = r.value
                    val loadedStr = loaded.toString(Charsets.UTF_8)
                    val equal = payload.contentEquals(loaded)
                    Log.i(TAG, "loadOwn OK: ${loadedStr} (len=${loaded.size})")
                    if (equal) {
                        Log.i(TAG, "✅ ROUND-TRIP PASS — byte-equal")
                    } else {
                        Log.e(TAG, "❌ ROUND-TRIP FAIL — mismatch")
                        Log.e(TAG, "  sent: ${payload.joinToString(",") { it.toString() }}")
                        Log.e(TAG, "  recv: ${loaded.joinToString(",") { it.toString() }}")
                    }
                }
                is Outcome.Failure -> Log.e(TAG, "loadOwn FAILED: ${r.error}")
            }
        }
    }

    companion object {
        const val ACTION = "com.launcher.app.DEBUG_F5B_ROUNDTRIP"
        private const val CONFIG_NAME = "debug-rt"
        private const val TAG = "F5bRoundtrip"
    }
}
