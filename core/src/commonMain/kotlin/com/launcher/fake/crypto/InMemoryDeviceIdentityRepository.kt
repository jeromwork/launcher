package com.launcher.fake.crypto

import family.crypto.exception.CryptoException
import family.pairing.api.DeviceId
import family.pairing.api.DeviceIdentity
import family.pairing.api.DeviceIdentityRepository

// mockBackend flavor — in-memory implementation. No signature verification
// (signature semantic в mockBackend не нужно — нет network impersonation).
// Production: FirestoreDeviceIdentityRepository (realBackend).
//
// TASK-51 Phase 6 — Outcome<T, CryptoError> → throws CryptoException.
class InMemoryDeviceIdentityRepository : DeviceIdentityRepository {

    private data class Key(val linkId: String, val deviceId: DeviceId)
    private val store = mutableMapOf<Key, DeviceIdentity>()

    override suspend fun publishOwn(linkId: String, identity: DeviceIdentity) {
        store[Key(linkId, identity.deviceId)] = identity
    }

    override suspend fun fetchPeer(linkId: String, peerDeviceId: DeviceId): DeviceIdentity {
        return store[Key(linkId, peerDeviceId)]
            ?: throw CryptoException.SerializationException("peer not found")
    }

    override suspend fun listAll(linkId: String): List<DeviceIdentity> =
        store.entries.filter { it.key.linkId == linkId }.map { it.value }
}
