package com.launcher.adapters.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.launcher.api.push.FcmReceiverContract
import com.launcher.api.push.PushReceiver
import family.push.api.BackgroundDispatcher
import family.push.api.EventType
import family.push.api.FcmTokenPublisher
import family.push.api.PushHandlerRegistry
import family.push.api.PushPayload
import family.push.api.WireFormatVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext

/**
 * FCM data-message entry point on the device (FR-016, FR-037 spec 007; F-5c
 * FR-028 spec 019).
 *
 * **Dispatch fork** (Phase 4 receiver-side migration):
 *  • If payload contains new `eventType` field (F-5c shape) → look up
 *    [PushHandlerRegistry] via Koin → dispatch via [BackgroundDispatcher].
 *  • Else if contains legacy `type` field (spec 007/008 shape) →
 *    [FcmReceiverContract.parseFcmDataMap] → [PushReceiver.onPush].
 *  • Else — malformed / unknown → log + drop (FR-075 silent fail-soft).
 *
 * Existing legacy path preserved for backward compatibility with already
 * deployed devices running spec 007/008 payloads.
 *
 * **`onNewToken`** — invokes [FcmTokenPublisher.publish] (F-5c FR-027) so
 * Firestore directory entry gets the rotated token.
 *
 * Manifest registration: `app/src/realBackend/AndroidManifest.xml`. Only the
 * realBackend flavor registers this service.
 */
class LauncherFirebaseMessagingService : FirebaseMessagingService() {

    private val pushReceiver: PushReceiver by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) {
            Log.w(TAG, "FCM data-message empty — drop")
            return
        }

        when {
            data.containsKey(KEY_EVENT_TYPE) -> dispatchNewShape(data)
            data.containsKey(KEY_LEGACY_TYPE) -> dispatchLegacyShape(data)
            else -> Log.w(TAG, "FCM payload missing both eventType + type — drop")
        }
    }

    /** F-5c new-shape dispatch — PushHandlerRegistry path. */
    private fun dispatchNewShape(data: Map<String, String>) {
        val payload = PushPayload.parseFromFcmData(data)
        if (payload == null) {
            Log.w(TAG, "F-5c payload malformed — drop: $data")
            return
        }
        if (payload.schemaVersion > WireFormatVersion.MAX_SUPPORTED_SCHEMA_VERSION) {
            Log.w(TAG, "F-5c payload schemaVersion=${payload.schemaVersion} unsupported — drop")
            return
        }
        val eventType = EventType.fromWireOrNull(payload.eventType)
        if (eventType == null) {
            Log.w(TAG, "F-5c unknown eventType=${payload.eventType} — drop")
            return
        }
        val registry = runCatching {
            GlobalContext.get().getOrNull<PushHandlerRegistry>()
        }.getOrNull()
        if (registry == null) {
            Log.w(TAG, "F-5c PushHandlerRegistry not wired в Koin — drop")
            return
        }
        val handler = registry.handlerFor(eventType)
        if (handler == null) {
            Log.w(TAG, "F-5c no handler for eventType=${payload.eventType} — drop")
            return
        }
        val dispatcher = runCatching {
            GlobalContext.get().getOrNull<BackgroundDispatcher>()
        }.getOrNull()
        if (dispatcher == null) {
            Log.w(TAG, "F-5c BackgroundDispatcher not wired в Koin — drop")
            return
        }

        scope.launch {
            try {
                dispatcher.dispatch(
                    taskName = "push-${payload.eventType}-${payload.triggerId}",
                    timeout = eventType.handlerTimeout,
                ) {
                    handler.handle(payload)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "F-5c handler failed (eventually via pull-on-app-open): ${t.message}")
            }
        }
    }

    /** Legacy spec 007/008 dispatch — PushReceiver path. Unchanged behaviour. */
    private fun dispatchLegacyShape(data: Map<String, String>) {
        val payload = FcmReceiverContract.parseFcmDataMap(data) ?: run {
            Log.w(TAG, "legacy FCM payload malformed — drop: $data")
            return
        }
        scope.launch { pushReceiver.onPush(payload) }
    }

    /**
     * T142 (Phase 4) — publish rotated FCM token к Firestore directory entry.
     * Per spec 019 FR-027.
     */
    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token rotated")
        val publisher = runCatching {
            GlobalContext.get().getOrNull<FcmTokenPublisher>()
        }.getOrNull()
        if (publisher == null) {
            Log.w(TAG, "FcmTokenPublisher not wired в Koin — token not persisted")
            return
        }
        scope.launch {
            runCatching { publisher.publish(token) }.onFailure {
                Log.w(TAG, "FCM token publish failed: ${it.message}")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LauncherFcm"
        private const val KEY_EVENT_TYPE = "eventType"
        private const val KEY_LEGACY_TYPE = "type"
    }
}
