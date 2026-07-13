package cryptokit.keys.fakes

import cryptokit.keys.api.DeviceId
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecipientPubKey
import cryptokit.keys.api.internal.DirectoryError
import cryptokit.keys.api.internal.PublicKeyDirectory

/**
 * In-memory [PublicKeyDirectory] for tests. Mirrors the shape of the
 * production InMemoryPublicKeyDirectory adapter but lives in commonTest so
 * domain-layer tests can reuse it without depending on the app module.
 */
class FakePublicKeyDirectory : PublicKeyDirectory {

    private val devices = mutableMapOf<String, MutableMap<String, RecipientPubKey>>()
    private val grants = mutableMapOf<String, MutableSet<String>>()

    fun seedGrant(ownerUid: String, helperUid: String) {
        grants.getOrPut(ownerUid) { mutableSetOf() }.add(helperUid)
    }

    override suspend fun publishMyDevice(
        myUid: String,
        myDeviceId: DeviceId,
        pubKey: ByteArray
    ): Outcome<Unit, DirectoryError> {
        devices.getOrPut(myUid) { mutableMapOf() }[myDeviceId.value] =
            RecipientPubKey(myDeviceId, pubKey.copyOf())
        return Outcome.Success(Unit)
    }

    override suspend fun fetchDevicesFor(
        ownerUid: String
    ): Outcome<List<RecipientPubKey>, DirectoryError> {
        return Outcome.Success(devices[ownerUid]?.values?.toList() ?: emptyList())
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
