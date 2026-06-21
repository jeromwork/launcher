package family.push.api

/**
 * T023 — Publishes FCM device token к Firestore directory entry. Per spec 019
 * FR-027, data-model.md §FcmTokenPublisher.
 *
 * Lifecycle (caller responsibility):
 *  • Initial publish: after Sign-In + F-5b EnvelopeBootstrap.bootstrap() completed.
 *  • Updates: FirebaseMessagingService.onNewToken(token) callback → publish(newToken).
 *  • Removal: sign-out (F-4 territory). F-5c does NOT control deletion.
 *
 * Idempotent: повторный publish с тем же token — no-op (Firestore merge update).
 *
 * Wire format: writes ONLY `fcmToken` + `fcmTokenUpdatedAt` к existing F-5b
 * `/users/{uid}/devices/{deviceId}` entry (merge update, не overwrite envelope
 * keys). Per data-model.md §RecipientDeviceEntry.
 */
interface FcmTokenPublisher {
    suspend fun publish(fcmToken: String): Outcome<Unit, FcmTokenPublisherError>
}

/**
 * Failure variants. Adding new variant — additive change (caller `when` should
 * have `else` branch).
 */
sealed class FcmTokenPublisherError(open val message: String) {

    /** Not signed-in — caller invoked publish() before AuthIdentity established UID. */
    data object NoIdentity : FcmTokenPublisherError("Not signed in; cannot publish FCM token")

    /** Firestore write failed — network, permission, etc. */
    data class Backend(override val message: String) : FcmTokenPublisherError(message)
}
