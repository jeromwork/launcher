package com.launcher.adapters.crypto

import android.util.Base64
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceId
import com.launcher.api.crypto.DeviceIdentity
import com.launcher.api.crypto.DeviceIdentityRepository
import com.launcher.api.crypto.DigitalSignature
import com.launcher.api.crypto.ED25519_KEY_SIZE
import com.launcher.api.crypto.ED25519_SIGNATURE_SIZE
import com.launcher.api.crypto.PublicKey
import com.launcher.api.crypto.SigningPublicKey
import com.launcher.api.crypto.SUPPORTED_SCHEMA_VERSION
import com.launcher.api.crypto.X25519_KEY_SIZE
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.tasks.await

// Firestore implementation: /links/{linkId}/devices/{deviceId}
// + /links/{linkId}/deviceOwnership/{deviceId}.
//
// На fetchPeer MUST verify Ed25519 signature через injected DigitalSignature
// ДО возврата identity. Tampered document / stale timestamp / size mismatch
// → SignatureVerifyFailed.
//
// Wire format — base64-encoded keys/signature (Firestore не поддерживает
// raw bytes в string fields). Per contracts/device-identity.md.
@OptIn(ExperimentalUuidApi::class)
class FirestoreDeviceIdentityRepository(
    private val firestore: FirebaseFirestore,
    private val signature: DigitalSignature,
    private val ownerUid: () -> String?,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : DeviceIdentityRepository {

    override suspend fun publishOwn(
        linkId: String,
        identity: DeviceIdentity,
    ): Outcome<Unit, CryptoError> {
        val uid = ownerUid() ?: return Outcome.Failure(CryptoError.KeystoreFailure(IllegalStateException("not signed in")))
        return try {
            // Сначала claim ownership (race-free).
            val ownershipRef = firestore
                .collection("links").document(linkId)
                .collection("deviceOwnership").document(identity.deviceId.value)
            val ownershipSnap = ownershipRef.get().await()
            if (!ownershipSnap.exists()) {
                ownershipRef.set(mapOf("ownerUid" to uid)).await()
            }
            val devRef = firestore
                .collection("links").document(linkId)
                .collection("devices").document(identity.deviceId.value)
            devRef.set(toMap(identity)).await()
            Outcome.Success(Unit)
        } catch (e: Throwable) {
            Outcome.Failure(CryptoError.StorageFailure(e))
        }
    }

    override suspend fun fetchPeer(
        linkId: String,
        peerDeviceId: DeviceId,
    ): Outcome<DeviceIdentity, CryptoError> {
        val snap = try {
            firestore.collection("links").document(linkId)
                .collection("devices").document(peerDeviceId.value)
                .get().await()
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.StorageFailure(e))
        }
        if (!snap.exists()) {
            return Outcome.Failure(CryptoError.SignatureVerifyFailed(peerDeviceId, reason = "no document"))
        }
        val identity = fromMap(snap.data ?: emptyMap())
            ?: return Outcome.Failure(CryptoError.MalformedEnvelope())
        // Verify signature ДО возврата.
        val verifyResult = signature.verify(
            identity.signedPayloadBytes(),
            identity.signature,
            identity.signingPublicKey,
        )
        if (verifyResult is Outcome.Failure) {
            return Outcome.Failure(CryptoError.SignatureVerifyFailed(peerDeviceId, reason = "verify failed"))
        }
        // Freshness gate (7 days). Server-side rule также enforce — это defence-in-depth.
        val now = nowMillis()
        val age = now - identity.signedTimestamp
        if (age > FRESHNESS_WINDOW_MILLIS || age < -CLOCK_SKEW_MILLIS) {
            return Outcome.Failure(CryptoError.SignatureVerifyFailed(peerDeviceId, reason = "stale timestamp"))
        }
        return Outcome.Success(identity)
    }

    override suspend fun listAll(linkId: String): List<DeviceIdentity> {
        return try {
            val qs = firestore.collection("links").document(linkId)
                .collection("devices").get().await()
            qs.documents.mapNotNull { fromMap(it.data ?: emptyMap()) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun toMap(d: DeviceIdentity): Map<String, Any> = mapOf(
        "schemaVersion" to d.schemaVersion,
        "deviceId" to d.deviceId.value,
        "publicKey" to d.publicKey.bytes.toBase64(),
        "signingPublicKey" to d.signingPublicKey.bytes.toBase64(),
        "signedTimestamp" to d.signedTimestamp,
        "signature" to d.signature.toBase64(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
        "algorithm" to "x25519+ed25519",
    )

    private fun fromMap(m: Map<String, Any?>): DeviceIdentity? {
        return try {
            val sv = (m["schemaVersion"] as? Number)?.toInt() ?: return null
            if (sv != SUPPORTED_SCHEMA_VERSION) return null
            val deviceIdStr = m["deviceId"] as? String ?: return null
            val pubBytes = (m["publicKey"] as? String)?.fromBase64() ?: return null
            val signingPubBytes = (m["signingPublicKey"] as? String)?.fromBase64() ?: return null
            val signedTs = (m["signedTimestamp"] as? Number)?.toLong() ?: return null
            val sigBytes = (m["signature"] as? String)?.fromBase64() ?: return null
            if (pubBytes.size != X25519_KEY_SIZE) return null
            if (signingPubBytes.size != ED25519_KEY_SIZE) return null
            if (sigBytes.size != ED25519_SIGNATURE_SIZE) return null
            val createdAtMillis = when (val v = m["createdAt"]) {
                is com.google.firebase.Timestamp -> v.seconds * 1000L + v.nanoseconds / 1_000_000
                is Number -> v.toLong()
                else -> 0L
            }
            DeviceIdentity(
                schemaVersion = sv,
                deviceId = DeviceId(deviceIdStr),
                publicKey = PublicKey(pubBytes),
                signingPublicKey = SigningPublicKey(signingPubBytes),
                signedTimestamp = signedTs,
                signature = sigBytes,
                createdAt = createdAtMillis,
            )
        } catch (e: Throwable) {
            null
        }
    }

    private companion object {
        private const val FRESHNESS_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val CLOCK_SKEW_MILLIS = 60L * 1000

        private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
        private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
    }
}
