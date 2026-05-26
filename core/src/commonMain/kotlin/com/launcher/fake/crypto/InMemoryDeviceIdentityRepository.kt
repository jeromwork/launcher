package com.launcher.fake.crypto

import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceId
import com.launcher.api.crypto.DeviceIdentity
import com.launcher.api.crypto.DeviceIdentityRepository
import com.launcher.api.result.Outcome

// mockBackend flavor — in-memory implementation. No signature verification
// (signature semantic в mockBackend не нужно — нет network impersonation).
// Production: FirestoreDeviceIdentityRepository (realBackend).
class InMemoryDeviceIdentityRepository : DeviceIdentityRepository {

    private data class Key(val linkId: String, val deviceId: DeviceId)
    private val store = mutableMapOf<Key, DeviceIdentity>()

    override suspend fun publishOwn(
        linkId: String,
        identity: DeviceIdentity,
    ): Outcome<Unit, CryptoError> {
        store[Key(linkId, identity.deviceId)] = identity
        return Outcome.Success(Unit)
    }

    override suspend fun fetchPeer(
        linkId: String,
        peerDeviceId: DeviceId,
    ): Outcome<DeviceIdentity, CryptoError> {
        val identity = store[Key(linkId, peerDeviceId)]
            ?: return Outcome.Failure(CryptoError.SignatureVerifyFailed(peerDeviceId, reason = "not found"))
        return Outcome.Success(identity)
    }

    override suspend fun listAll(linkId: String): List<DeviceIdentity> =
        store.entries.filter { it.key.linkId == linkId }.map { it.value }
}
