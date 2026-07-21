package com.launcher.adapters.crypto

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import family.crypto.api.AsymmetricCrypto
import family.crypto.api.values.Signature
import family.crypto.exception.CryptoException
import family.pairing.api.DeviceId
import family.pairing.api.DeviceIdentity
import family.pairing.api.DeviceIdentityRepository
import family.pairing.api.ED25519_KEY_SIZE
import family.pairing.api.ED25519_SIGNATURE_SIZE
import family.pairing.api.PublicKey
import family.pairing.api.SigningPublicKey
import family.pairing.api.X25519_KEY_SIZE
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// Firestore implementation: /links/{linkId}/devices/{deviceId}
// + /links/{linkId}/deviceOwnership/{deviceId}.
//
// На fetchPeer MUST verify Ed25519 signature через injected AsymmetricCrypto
// ДО возврата identity. Tampered document / stale timestamp / size mismatch
// → CryptoException.DecryptionFailed (verify-fail semantically maps к нему,
// данные fingerprint'aются как auth-failed).
//
// Wire format — base64-encoded keys/signature (Firestore не поддерживает
// raw bytes в string fields). Per contracts/device-identity.md.
//
// TASK-51 Phase 6: переписан с Outcome<T, CryptoError> на throws CryptoException;
// inject AsymmetricCrypto (cryptokit) вместо legacy DigitalSignature port;
// universal logging contract (FR-017) — operation / exceptionClass / messageHash,
// никаких raw bytes / deviceIds в логах.
@OptIn(ExperimentalUuidApi::class)
class FirestoreDeviceIdentityRepository(
    private val firestore: FirebaseFirestore,
    private val asymmetric: AsymmetricCrypto,
    private val ownerUid: () -> String?,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : DeviceIdentityRepository {

    override suspend fun publishOwn(linkId: String, identity: DeviceIdentity) {
        withCryptoLogging("publishOwn") {
            val uid = ownerUid() ?: throw CryptoException.KeyStoreException("not signed in")
            try {
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
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: CryptoException) {
                throw e
            } catch (e: Throwable) {
                throw CryptoException.SerializationException("publishOwn Firestore failure", e)
            }
        }
    }

    override suspend fun fetchPeer(linkId: String, peerDeviceId: DeviceId): DeviceIdentity =
        withCryptoLogging("fetchPeer") {
            val snap = try {
                firestore.collection("links").document(linkId)
                    .collection("devices").document(peerDeviceId.value)
                    .get().await()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                throw CryptoException.SerializationException("fetchPeer Firestore failure", e)
            }
            if (!snap.exists()) {
                throw CryptoException.SerializationException("peer document missing")
            }
            val identity = fromMap(snap.data ?: emptyMap())
                ?: throw CryptoException.SerializationException("peer document malformed")
            // Verify Ed25519 signature ДО возврата.
            val ok = try {
                asymmetric.verify(
                    Signature(identity.signature),
                    identity.signedPayloadBytes(),
                    identity.signingPublicKey.bytes,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                throw CryptoException.DecryptionFailed("signature verify threw: ${e.javaClass.simpleName}")
            }
            if (!ok) {
                throw CryptoException.DecryptionFailed("signature verify failed")
            }
            // Freshness gate (7 days). Server-side rule также enforce — это defence-in-depth.
            val now = nowMillis()
            val age = now - identity.signedTimestamp
            if (age > FRESHNESS_WINDOW_MILLIS || age < -CLOCK_SKEW_MILLIS) {
                throw CryptoException.DecryptionFailed("stale timestamp")
            }
            identity
        }

    override suspend fun listAll(linkId: String): List<DeviceIdentity> {
        return try {
            val qs = firestore.collection("links").document(linkId)
                .collection("devices").get().await()
            qs.documents.mapNotNull { fromMap(it.data ?: emptyMap()) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend inline fun <T> withCryptoLogging(
        operation: String,
        block: () -> T,
    ): T = try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: CryptoException) {
        Log.w(
            LOG_TAG,
            "operation=$operation exceptionClass=${e.javaClass.simpleName} " +
                "messageHash=${e.message?.hashCode()}",
        )
        throw e
    } catch (e: Throwable) {
        Log.w(
            LOG_TAG,
            "operation=$operation exceptionClass=${e.javaClass.simpleName} " +
                "messageHash=${e.message?.hashCode()}",
        )
        throw CryptoException.SerializationException("unexpected $operation failure", e)
    }

    companion object {
        private const val FRESHNESS_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val CLOCK_SKEW_MILLIS = 60L * 1000

        private const val LOG_TAG: String = "cryptokit"

        // TASK-141 — DeviceIdentity carries no schemaVersion of its own (rule 1);
        // the wire version lives here, in the adapter that owns the Firestore
        // document `/links/{linkId}/devices/{deviceId}`. toMap stamps it, fromMap
        // gates on it. If this format ever moves to a JSON-string blob, the
        // standardized form is a @Serializable DTO class in this package.
        internal const val WIRE_SCHEMA_VERSION: Int = 1

        internal fun toMap(d: DeviceIdentity): Map<String, Any> = mapOf(
            "schemaVersion" to WIRE_SCHEMA_VERSION,
            "deviceId" to d.deviceId.value,
            "publicKey" to d.publicKey.bytes.toBase64(),
            "signingPublicKey" to d.signingPublicKey.bytes.toBase64(),
            "signedTimestamp" to d.signedTimestamp,
            "signature" to d.signature.toBase64(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "algorithm" to "x25519+ed25519",
        )

        internal fun fromMap(m: Map<String, Any?>): DeviceIdentity? {
            return try {
                val sv = (m["schemaVersion"] as? Number)?.toInt() ?: return null
                if (sv != WIRE_SCHEMA_VERSION) return null
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
                    deviceId = DeviceId(deviceIdStr),
                    publicKey = PublicKey(pubBytes),
                    signingPublicKey = SigningPublicKey(signingPubBytes),
                    signedTimestamp = signedTs,
                    signature = sigBytes,
                    createdAt = createdAtMillis,
                )
            } catch (_: Throwable) {
                null
            }
        }

        private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
        private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
    }
}
