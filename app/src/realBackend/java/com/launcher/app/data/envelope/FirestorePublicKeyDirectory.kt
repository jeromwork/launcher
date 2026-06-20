package com.launcher.app.data.envelope

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import family.keys.api.DeviceId
import family.keys.api.Outcome
import family.keys.api.RecipientPubKey
import family.keys.api.internal.DirectoryError
import family.keys.api.internal.PublicKeyDirectory
import kotlinx.coroutines.tasks.await

/**
 * Firestore adapter for [PublicKeyDirectory] — F-5b Batch 2.
 *
 * **Document paths**:
 *  - Device pub key: `/users/{uid}/devices/{deviceId}/pub-key/current`
 *  - Access grant: `/users/{uid}/access-grants/{helperUid}` (created/managed by
 *    pairing flow; this adapter only **reads** to discover grant holders).
 *
 * **Field mapping** for device pub-key document:
 *  - `schemaVersion: int`
 *  - `pubKey: bytes` (32-byte X25519)
 *  - `algorithm: string` (= "x25519-raw-v1")
 *  - `createdAt: timestamp`
 *
 * **Field mapping** for access-grant document:
 *  - `permissions: array<string>` (e.g., `["read", "write"]`)
 *  - `grantedAt: timestamp`
 *  - `revokedAt: timestamp?` (null when active)
 *
 * **Security Rules** (extension of firestore.rules — added in Batch 4):
 *  - Owner can write to their own `/users/{uid}/devices/{*}/pub-key/current`.
 *  - Read access for everyone with a valid (non-revoked) grant in
 *    `/users/{uid}/access-grants/{theirUid}`.
 *  - Owner can write/revoke `/users/{uid}/access-grants/{*}`.
 *
 * TODO(server-roadmap SRV-PKD-001): own-server replacement publishes pub keys
 * via REST endpoint with JWT auth. Domain port unchanged.
 */
class FirestorePublicKeyDirectory(
    private val firestore: FirebaseFirestore
) : PublicKeyDirectory {

    private fun deviceDoc(uid: String, deviceId: DeviceId) =
        firestore.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_DEVICES).document(deviceId.value)

    private fun pubKeyDoc(uid: String, deviceId: DeviceId) =
        deviceDoc(uid, deviceId).collection(COLLECTION_PUB_KEY).document(DOCUMENT_CURRENT)

    private fun devicesCollection(uid: String) =
        firestore.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_DEVICES)

    private fun grantsCollection(uid: String) =
        firestore.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_GRANTS)

    override suspend fun publishMyDevice(
        myUid: String,
        myDeviceId: DeviceId,
        pubKey: ByteArray
    ): Outcome<Unit, DirectoryError> {
        if (myUid.isEmpty()) return Outcome.Failure(DirectoryError.Unauthorized)
        require(pubKey.size == 32) { "X25519 pub key must be 32 bytes" }
        return try {
            // Firestore queries return only documents that have been
            // explicitly written. Without a marker doc at
            // `/users/{uid}/devices/{deviceId}`, [fetchDevicesFor] cannot see
            // this device — Firestore does not surface virtual parents of
            // subcollections in a collection `.get()`. Write the marker
            // alongside the pub-key blob so resolution is consistent.
            firestore.runBatch { batch ->
                batch.set(
                    deviceDoc(myUid, myDeviceId),
                    mapOf(
                        FIELD_SCHEMA_VERSION to PUBKEY_SCHEMA_VERSION,
                        FIELD_CREATED_AT to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    pubKeyDoc(myUid, myDeviceId),
                    mapOf(
                        FIELD_SCHEMA_VERSION to PUBKEY_SCHEMA_VERSION,
                        FIELD_PUB_KEY to Blob.fromBytes(pubKey),
                        FIELD_ALGORITHM to ALGORITHM_X25519,
                        FIELD_CREATED_AT to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(mapFirestore(e))
        } catch (t: Throwable) {
            Outcome.Failure(DirectoryError.Network(t))
        }
    }

    override suspend fun fetchDevicesFor(
        ownerUid: String
    ): Outcome<List<RecipientPubKey>, DirectoryError> {
        if (ownerUid.isEmpty()) return Outcome.Failure(DirectoryError.Unauthorized)
        return try {
            // Collection-group-like query: fetch each device's pub-key/current sub-doc.
            val devices = devicesCollection(ownerUid).get().await()
            val recipients = mutableListOf<RecipientPubKey>()
            for (deviceDoc in devices.documents) {
                val deviceIdStr = deviceDoc.id
                val pubKeySnap = deviceDoc.reference
                    .collection(COLLECTION_PUB_KEY).document(DOCUMENT_CURRENT)
                    .get().await()
                if (!pubKeySnap.exists()) continue
                val pubKey = (pubKeySnap.get(FIELD_PUB_KEY) as? Blob)?.toBytes() ?: continue
                if (pubKey.size != 32) continue
                recipients += RecipientPubKey(DeviceId(deviceIdStr), pubKey)
            }
            Outcome.Success(recipients)
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(mapFirestore(e))
        } catch (t: Throwable) {
            Outcome.Failure(DirectoryError.Network(t))
        }
    }

    override suspend fun fetchGrantHolders(
        ownerUid: String
    ): Outcome<List<String>, DirectoryError> {
        if (ownerUid.isEmpty()) return Outcome.Failure(DirectoryError.Unauthorized)
        return try {
            val snap = grantsCollection(ownerUid).get().await()
            val holders = snap.documents.mapNotNull { doc ->
                // Skip revoked grants.
                if (doc.get(FIELD_REVOKED_AT) != null) return@mapNotNull null
                doc.id // helper UID
            }
            Outcome.Success(holders)
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(mapFirestore(e))
        } catch (t: Throwable) {
            Outcome.Failure(DirectoryError.Network(t))
        }
    }

    override suspend fun unpublishMyDevice(
        myUid: String,
        myDeviceId: DeviceId
    ): Outcome<Unit, DirectoryError> {
        if (myUid.isEmpty()) return Outcome.Failure(DirectoryError.Unauthorized)
        return try {
            pubKeyDoc(myUid, myDeviceId).delete().await()
            // Also delete the parent device doc; it's now empty.
            devicesCollection(myUid).document(myDeviceId.value).delete().await()
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(mapFirestore(e))
        } catch (t: Throwable) {
            Outcome.Failure(DirectoryError.Network(t))
        }
    }

    private fun mapFirestore(e: FirebaseFirestoreException): DirectoryError = when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> DirectoryError.Unauthorized
        FirebaseFirestoreException.Code.NOT_FOUND -> DirectoryError.NotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> DirectoryError.Network(e)
        else -> DirectoryError.Network(e)
    }

    companion object {
        const val COLLECTION_USERS: String = "users"
        const val COLLECTION_DEVICES: String = "devices"
        const val COLLECTION_PUB_KEY: String = "pub-key"
        const val COLLECTION_GRANTS: String = "access-grants"
        const val DOCUMENT_CURRENT: String = "current"

        const val FIELD_SCHEMA_VERSION: String = "schemaVersion"
        const val FIELD_PUB_KEY: String = "pubKey"
        const val FIELD_ALGORITHM: String = "algorithm"
        const val FIELD_CREATED_AT: String = "createdAt"
        const val FIELD_REVOKED_AT: String = "revokedAt"

        const val PUBKEY_SCHEMA_VERSION: Int = 1
        const val ALGORITHM_X25519: String = "x25519-raw-v1"
    }
}
