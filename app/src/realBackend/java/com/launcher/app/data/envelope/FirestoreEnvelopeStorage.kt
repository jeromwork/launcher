package com.launcher.app.data.envelope

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import cryptokit.keys.api.Envelope
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.internal.EnvelopeStorage
import cryptokit.keys.api.internal.EnvelopeStorageError
import kotlinx.coroutines.tasks.await

/**
 * Firestore adapter for [EnvelopeStorage] — F-5b Batch 2.
 *
 * Firebase Firestore SDK is imported **only here** (and other realBackend
 * adapters); domain (`:core:keys`) does not know about Firebase per CLAUDE.md
 * rule 1.
 *
 * **Document path**: `/users/{namespace}/data/{escapedKey}`.
 *
 * **Key escaping**: Firestore document IDs forbid `/` and a few control
 * characters. We use **base64url(no padding)** of UTF-8 bytes — reversible,
 * safe across all characters, no escape collisions. The original key is also
 * stored as a field (`logicalKey`) for query / listing.
 *
 * **Field mapping** (native Firestore typed fields, NOT a single JSON string):
 *  - `schemaVersion: int`
 *  - `algorithm: string`
 *  - `ciphertext: bytes`
 *  - `nonce: bytes`
 *  - `aad: bytes`
 *  - `recipientKeys: map<string, bytes>` (deviceId → sealed CEK)
 *  - `logicalKey: string` (original key for listing)
 *  - `updatedAt: timestamp` (server timestamp)
 *
 * **Security**: Firestore Security Rules enforce owner-only write + grant-based
 * read; this adapter does not re-check on client.
 *
 * TODO(server-roadmap SRV-STORAGE-001): when migrating to own server, replace
 * with `OwnServerEnvelopeStorage` (HTTPS + JWT). Domain port unchanged.
 */
class FirestoreEnvelopeStorage(
    private val firestore: FirebaseFirestore
) : EnvelopeStorage {

    private fun docRef(namespace: String, key: String) =
        firestore.collection(COLLECTION_USERS).document(namespace)
            .collection(COLLECTION_DATA).document(escape(key))

    override suspend fun store(
        namespace: String,
        key: String,
        envelope: Envelope
    ): Outcome<Unit, EnvelopeStorageError> {
        if (namespace.isEmpty()) return Outcome.Failure(EnvelopeStorageError.Unauthorized)
        if (key.isEmpty()) return Outcome.Failure(EnvelopeStorageError.Malformed("key must not be empty"))
        return try {
            docRef(namespace, key).set(encode(envelope, key)).await()
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(mapFirestore(e))
        } catch (t: Throwable) {
            Outcome.Failure(EnvelopeStorageError.Network(t))
        }
    }

    override suspend fun load(
        namespace: String,
        key: String
    ): Outcome<Envelope, EnvelopeStorageError> {
        if (namespace.isEmpty()) return Outcome.Failure(EnvelopeStorageError.Unauthorized)
        if (key.isEmpty()) return Outcome.Failure(EnvelopeStorageError.Malformed("key must not be empty"))
        return try {
            val snap = docRef(namespace, key).get().await()
            if (!snap.exists()) return Outcome.Failure(EnvelopeStorageError.NotFound)
            val envelope = decode(snap.data ?: emptyMap())
                ?: return Outcome.Failure(EnvelopeStorageError.Malformed("decode failed"))
            Outcome.Success(envelope)
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(mapFirestore(e))
        } catch (t: Throwable) {
            Outcome.Failure(EnvelopeStorageError.Malformed(t.message ?: "load failed"))
        }
    }

    override suspend fun list(
        namespace: String,
        keyPrefix: String
    ): Outcome<List<String>, EnvelopeStorageError> {
        if (namespace.isEmpty()) return Outcome.Failure(EnvelopeStorageError.Unauthorized)
        return try {
            val snap = firestore.collection(COLLECTION_USERS).document(namespace)
                .collection(COLLECTION_DATA).get().await()
            val keys = snap.documents.mapNotNull { doc ->
                val logicalKey = doc.getString(FIELD_LOGICAL_KEY) ?: return@mapNotNull null
                logicalKey.takeIf { it.startsWith(keyPrefix) }
            }.sorted()
            Outcome.Success(keys)
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(mapFirestore(e))
        } catch (t: Throwable) {
            Outcome.Failure(EnvelopeStorageError.Network(t))
        }
    }

    override suspend fun delete(
        namespace: String,
        key: String
    ): Outcome<Unit, EnvelopeStorageError> {
        if (namespace.isEmpty()) return Outcome.Failure(EnvelopeStorageError.Unauthorized)
        return try {
            docRef(namespace, key).delete().await()
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(mapFirestore(e))
        } catch (t: Throwable) {
            Outcome.Failure(EnvelopeStorageError.Network(t))
        }
    }

    private fun encode(envelope: Envelope, logicalKey: String): Map<String, Any> = mapOf(
        FIELD_SCHEMA_VERSION to envelope.schemaVersion,
        FIELD_ALGORITHM to envelope.algorithm,
        FIELD_CIPHERTEXT to Blob.fromBytes(envelope.ciphertext),
        FIELD_NONCE to Blob.fromBytes(envelope.nonce),
        FIELD_AAD to Blob.fromBytes(envelope.aad),
        FIELD_RECIPIENT_KEYS to envelope.recipientKeys.mapValues { Blob.fromBytes(it.value) },
        FIELD_LOGICAL_KEY to logicalKey,
        FIELD_UPDATED_AT to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    @Suppress("UNCHECKED_CAST")
    private fun decode(data: Map<String, Any>): Envelope? {
        return try {
            val schemaVersion = (data[FIELD_SCHEMA_VERSION] as? Number)?.toInt() ?: return null
            val algorithm = data[FIELD_ALGORITHM] as? String ?: return null
            val ciphertext = (data[FIELD_CIPHERTEXT] as? Blob)?.toBytes() ?: return null
            val nonce = (data[FIELD_NONCE] as? Blob)?.toBytes() ?: return null
            val aad = (data[FIELD_AAD] as? Blob)?.toBytes() ?: return null
            val rawRecipients = data[FIELD_RECIPIENT_KEYS] as? Map<String, Any> ?: return null
            val recipientKeysBuilder = mutableMapOf<String, ByteArray>()
            for ((k, v) in rawRecipients) {
                val bytes = (v as? Blob)?.toBytes() ?: return null
                recipientKeysBuilder[k] = bytes
            }
            if (recipientKeysBuilder.isEmpty()) return null
            Envelope(
                schemaVersion = schemaVersion,
                algorithm = algorithm,
                ciphertext = ciphertext,
                nonce = nonce,
                aad = aad,
                recipientKeys = recipientKeysBuilder
            )
        } catch (t: Throwable) {
            null
        }
    }

    private fun mapFirestore(e: FirebaseFirestoreException): EnvelopeStorageError = when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> EnvelopeStorageError.Unauthorized
        FirebaseFirestoreException.Code.NOT_FOUND -> EnvelopeStorageError.NotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> EnvelopeStorageError.Network(e)
        else -> EnvelopeStorageError.Network(e)
    }

    companion object {
        const val COLLECTION_USERS: String = "users"
        const val COLLECTION_DATA: String = "data"

        const val FIELD_SCHEMA_VERSION: String = "schemaVersion"
        const val FIELD_ALGORITHM: String = "algorithm"
        const val FIELD_CIPHERTEXT: String = "ciphertext"
        const val FIELD_NONCE: String = "nonce"
        const val FIELD_AAD: String = "aad"
        const val FIELD_RECIPIENT_KEYS: String = "recipientKeys"
        const val FIELD_LOGICAL_KEY: String = "logicalKey"
        const val FIELD_UPDATED_AT: String = "updatedAt"

        /** Escape a logical key into a Firestore-safe document id. base64url(no padding). */
        internal fun escape(key: String): String {
            val bytes = key.encodeToByteArray()
            return android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        }
    }
}
