package com.launcher.app.push

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import family.push.api.FcmTokenPublisher
import family.push.api.FcmTokenPublisherError
import family.push.api.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * T111 — Production [FcmTokenPublisher]. Per spec 019 FR-027.
 *
 * Writes FCM token к F-5b Firestore directory entry с merge update:
 *   `/users/{uid}/devices/{deviceId}` ← { fcmToken, fcmTokenUpdatedAt }.
 *
 * Lives in **app/realBackend** (not `:core:push`) per CLAUDE.md rule 2 (ACL):
 * Firebase SDK confined в realBackend flavor. `:core:push` остаётся
 * extraction-ready (verifyPushIsolation = zero project deps + no vendor SDKs
 * at module level).
 *
 * **Suppliers**: caller provides UID (F-4 AuthIdentity) + deviceId (F-5b
 * DeviceIdentity) via SAM functions — keeps adapter independent от identity
 * port concrete types.
 */
class FcmTokenPublisherImpl(
    private val firestore: FirebaseFirestore,
    private val uidSupplier: suspend () -> String?,
    private val deviceIdSupplier: suspend () -> String?,
) : FcmTokenPublisher {

    override suspend fun publish(fcmToken: String): Outcome<Unit, FcmTokenPublisherError> =
        withContext(Dispatchers.IO) {
            val uid = uidSupplier()
                ?: return@withContext Outcome.Failure(FcmTokenPublisherError.NoIdentity)
            val deviceId = deviceIdSupplier()
                ?: return@withContext Outcome.Failure(FcmTokenPublisherError.NoIdentity)

            try {
                firestore.collection("users")
                    .document(uid)
                    .collection("devices")
                    .document(deviceId)
                    .set(
                        mapOf(
                            "fcmToken" to fcmToken,
                            "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge(),
                    )
                    .await()
                Outcome.Success(Unit)
            } catch (t: Throwable) {
                Outcome.Failure(
                    FcmTokenPublisherError.Backend(t.message ?: "Firestore write failed"),
                )
            }
        }
}
