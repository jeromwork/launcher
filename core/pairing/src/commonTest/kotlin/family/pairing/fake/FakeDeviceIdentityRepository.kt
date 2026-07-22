package family.pairing.fake

import family.crypto.exception.CryptoException
import family.pairing.api.DeviceId
import family.pairing.api.DeviceIdentity
import family.pairing.api.DeviceIdentityRepository

/**
 * TEST-ONLY in-memory [DeviceIdentityRepository] — CLAUDE.md §6 mock-first.
 *
 * No signature verification (mockBackend semantics). Production verification is
 * the responsibility of FirestoreDeviceIdentityRepository.
 *
 * Configurable failure injection:
 *  - [failPublishWith] — if non-null, [publishOwn] throws this on next call.
 *  - [failFetchWith]  — if non-null, [fetchPeer] throws this on next call.
 */
class FakeDeviceIdentityRepository : DeviceIdentityRepository {

    private data class Key(val linkId: String, val deviceId: DeviceId)
    private val store: MutableMap<Key, DeviceIdentity> = mutableMapOf()

    var failPublishWith: CryptoException? = null
    var failFetchWith: CryptoException? = null

    /** Counters for assertion. */
    var publishCallCount: Int = 0
        private set
    var fetchCallCount: Int = 0
        private set

    override suspend fun publishOwn(linkId: String, identity: DeviceIdentity) {
        publishCallCount++
        failPublishWith?.let { ex -> failPublishWith = null; throw ex }
        store[Key(linkId, identity.deviceId)] = identity
    }

    override suspend fun fetchPeer(linkId: String, peerDeviceId: DeviceId): DeviceIdentity {
        fetchCallCount++
        failFetchWith?.let { ex -> failFetchWith = null; throw ex }
        return store[Key(linkId, peerDeviceId)]
            ?: throw CryptoException.SerializationException("peer not found: $peerDeviceId")
    }

    override suspend fun listAll(linkId: String): List<DeviceIdentity> =
        store.entries.filter { it.key.linkId == linkId }.map { it.value }

    fun clear() {
        store.clear()
        publishCallCount = 0
        fetchCallCount = 0
        failPublishWith = null
        failFetchWith = null
    }
}
