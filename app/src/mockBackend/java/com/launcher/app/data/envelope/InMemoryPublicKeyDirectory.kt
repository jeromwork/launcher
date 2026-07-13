package com.launcher.app.data.envelope

import cryptokit.keys.api.DeviceId
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecipientPubKey
import cryptokit.keys.api.internal.DirectoryError
import cryptokit.keys.api.internal.PublicKeyDirectory
import java.util.concurrent.ConcurrentHashMap

/**
 * mockBackend / dev-build implementation of [PublicKeyDirectory] — keeps
 * device pub-keys and grant lists in process memory.
 *
 * Access-grants are seeded externally for dev / smoke flows (no UI for pairing
 * in mockBackend variant); production paths use the Firestore variant.
 */
class InMemoryPublicKeyDirectory : PublicKeyDirectory {

    private data class DevicePub(val deviceId: DeviceId, val pubKey: ByteArray)

    private val devices = ConcurrentHashMap<String, ConcurrentHashMap<String, DevicePub>>()
    private val grants = ConcurrentHashMap<String, MutableSet<String>>()

    /** Dev hook — manually add a grant for smoke tests / preview. */
    fun seedGrant(ownerUid: String, helperUid: String) {
        grants.computeIfAbsent(ownerUid) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
            .add(helperUid)
    }

    override suspend fun publishMyDevice(
        myUid: String,
        myDeviceId: DeviceId,
        pubKey: ByteArray
    ): Outcome<Unit, DirectoryError> {
        if (myUid.isEmpty()) return Outcome.Failure(DirectoryError.Unauthorized)
        require(pubKey.size == 32) { "X25519 pub key must be 32 bytes" }
        devices.computeIfAbsent(myUid) { ConcurrentHashMap() }[myDeviceId.value] =
            DevicePub(myDeviceId, pubKey.copyOf())
        return Outcome.Success(Unit)
    }

    override suspend fun fetchDevicesFor(
        ownerUid: String
    ): Outcome<List<RecipientPubKey>, DirectoryError> {
        val list = devices[ownerUid]?.values?.map {
            RecipientPubKey(it.deviceId, it.pubKey.copyOf())
        } ?: emptyList()
        return Outcome.Success(list)
    }

    override suspend fun fetchGrantHolders(
        ownerUid: String
    ): Outcome<List<String>, DirectoryError> {
        return Outcome.Success(grants[ownerUid]?.toList() ?: emptyList())
    }

    override suspend fun unpublishMyDevice(
        myUid: String,
        myDeviceId: DeviceId
    ): Outcome<Unit, DirectoryError> {
        devices[myUid]?.remove(myDeviceId.value)
        return Outcome.Success(Unit)
    }
}
