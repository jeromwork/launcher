package com.launcher.api.push

/**
 * Pure function that converts the FCM `data: Map<String, String>` payload into
 * a domain [PushPayload]. Lives in `commonMain` (no Firebase SDK dep) so the
 * Android-side `LauncherFirebaseMessagingService` is a one-liner adapter:
 *
 * ```kotlin
 * override fun onMessageReceived(message: RemoteMessage) {
 *   val payload = FcmReceiverContract.parseFcmDataMap(message.data) ?: return
 *   pushReceiver.onPush(payload)
 * }
 * ```
 *
 * Makes the parsing testable without Robolectric.
 *
 * Returns `null` for malformed / unsupported payloads — caller MUST drop the
 * message silently per contracts/fcm-payload.md §Backward compatibility.
 */
object FcmReceiverContract {
    fun parseFcmDataMap(data: Map<String, String>): PushPayload? =
        PushPayloadWireFormat.parse(data)
}
