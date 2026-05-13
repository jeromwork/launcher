package com.launcher.adapters.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Topic subscribe/unsubscribe for FCM (FR-015, FR-033). Used by
 * `LinkRegistry.activate(...)` (subscribe) and `revoke()` (unsubscribe).
 *
 * The FCM SDK persists the subscription server-side; on app reinstall the
 * server forgets the subscription with the old FCM token and the new token
 * starts un-subscribed. Re-subscribe on pairing-restore is handled by
 * `LinkRegistry.activate` which is called from the pairing flow.
 *
 * Failures are silent here — `subscribeToTopic` returning a failed Task is
 * a "best-effort" signal in FCM (network drop, transient). The Firestore
 * listener path remains the source-of-truth and delivers updates eventually.
 *
 * TODO(reliability, project-backlog TODO-REL-001): wire an at-least-once
 * retry queue persisted in DataStore so subscribe failures on network drops
 * are retried at next foreground.
 */
class FcmRegistration(
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) {
    suspend fun subscribeToLinkTopic(linkId: String) {
        try {
            messaging.subscribeToTopic(topicFor(linkId)).await()
        } catch (_: Throwable) {
            // Best-effort: see kdoc.
        }
    }

    suspend fun unsubscribeFromLinkTopic(linkId: String) {
        try {
            messaging.unsubscribeFromTopic(topicFor(linkId)).await()
        } catch (_: Throwable) {
            // Best-effort.
        }
    }

    suspend fun currentFcmToken(): String? = try {
        messaging.token.await()
    } catch (_: Throwable) {
        null
    }

    private fun topicFor(linkId: String): String = "link-$linkId"
}
