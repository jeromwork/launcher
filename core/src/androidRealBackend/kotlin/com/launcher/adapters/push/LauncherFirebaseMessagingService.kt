package com.launcher.adapters.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.launcher.api.push.FcmReceiverContract
import com.launcher.api.push.PushReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * FCM data-message entry point on the Managed device (FR-016, FR-037).
 *
 *  - `onMessageReceived` → parses the FCM `data` map via
 *    [FcmReceiverContract.parseFcmDataMap] (pure-Kotlin, no Robolectric
 *    needed) → dispatches to the injected [PushReceiver].
 *  - `onNewToken` → invoked by FCM SDK after a Google-side token rotation
 *    (FR-017). The new token must be written to `/links/{linkId}/state.fcmToken`
 *    by the application — for spec 007 we log it; the actual write lives in
 *    spec 008's state-sync wiring.
 *
 * Manifest registration is done in `:app/src/realBackend/AndroidManifest.xml`
 * (see T056 acceptance) — only the realBackend flavor registers this service
 * so mockBackend builds don't pull in `com.google.firebase.MESSAGING_EVENT`
 * intent-filters.
 */
class LauncherFirebaseMessagingService : FirebaseMessagingService() {

    private val pushReceiver: PushReceiver by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = FcmReceiverContract.parseFcmDataMap(message.data) ?: run {
            Log.w(TAG, "dropped FCM data-message (malformed or unsupported): ${message.data}")
            return
        }
        scope.launch { pushReceiver.onPush(payload) }
    }

    override fun onNewToken(token: String) {
        // TODO(spec 008): write to /links/{linkId}/state.fcmToken via
        // RemoteSyncBackend.writeDoc(...) — needs current linkId from
        // LinkRegistry. Held back to spec 008 so spec 007 only owns the
        // wake-up path, not the state-sync surface.
        Log.i(TAG, "FCM token rotated; new=$token")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LauncherFcm"
    }
}
