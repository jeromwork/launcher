package cryptokit.keys.api.internal

import cryptokit.keys.api.DeviceId
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecipientPubKey

/**
 * Internal port: where each device's X25519 public key is published so other
 * devices (own + delegated helpers) can fetch it as a [RecipientPubKey].
 *
 * Backend layout (Firestore today, own server later):
 *  - `/users/{uid}/devices/{deviceId}/pub-key` stores `{schemaVersion, pubKey}`.
 *  - `/users/{uid}/access-grants/{helperUid}` enumerates helper UIDs with grants.
 *
 * Security Rules:
 *  - Owner can write own `/users/{uid}/devices/{*}`.
 *  - Anyone with a valid grant in `/users/{uid}/access-grants/{theirUid}` can
 *    read `/users/{uid}/devices/{*}/pub-key`.
 *
 * The directory does not know about envelope encryption — it only stores public
 * keys. [RecipientResolver] is the only consumer that turns published keys into
 * a recipient list.
 */
interface PublicKeyDirectory {

    /** Publish [pubKey] for `(myUid, myDeviceId)`. Idempotent overwrite. */
    suspend fun publishMyDevice(
        myUid: String,
        myDeviceId: DeviceId,
        pubKey: ByteArray
    ): Outcome<Unit, DirectoryError>

    /** Fetch all devices registered under [ownerUid] as [RecipientPubKey]. */
    suspend fun fetchDevicesFor(ownerUid: String): Outcome<List<RecipientPubKey>, DirectoryError>

    /**
     * List UIDs of helpers that hold a non-revoked grant from [ownerUid].
     * Used by [RecipientResolver] to expand grant-holders into their devices.
     */
    suspend fun fetchGrantHolders(ownerUid: String): Outcome<List<String>, DirectoryError>

    /** Remove this device's pub-key entry — called on sign-out. */
    suspend fun unpublishMyDevice(myUid: String, myDeviceId: DeviceId): Outcome<Unit, DirectoryError>
}

sealed class DirectoryError {
    data class Network(val cause: Throwable? = null) : DirectoryError()
    data object Unauthorized : DirectoryError()
    data object NotFound : DirectoryError()
    data class Malformed(val message: String) : DirectoryError()
}
