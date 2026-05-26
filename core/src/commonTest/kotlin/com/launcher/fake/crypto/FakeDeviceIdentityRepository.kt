package com.launcher.fake.crypto

import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceId
import com.launcher.api.crypto.DeviceIdentity
import com.launcher.api.crypto.DeviceIdentityRepository
import com.launcher.api.crypto.DigitalSignature
import com.launcher.api.result.Outcome

// Verifies Ed25519 signature через injected DigitalSignature ДО возврата identity.
// Tampered document → SignatureVerifyFailed; valid signature → identity.
class FakeDeviceIdentityRepository(
    private val signer: DigitalSignature = FakeDigitalSignature(),
) : DeviceIdentityRepository {

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
            ?: return Outcome.Failure(CryptoError.SignatureVerifyFailed(deviceId = peerDeviceId, reason = "not found"))
        val verify = signer.verify(identity.signedPayloadBytes(), identity.signature, identity.signingPublicKey)
        return when (verify) {
            is Outcome.Failure -> Outcome.Failure(CryptoError.SignatureVerifyFailed(deviceId = peerDeviceId, reason = "verify failed"))
            is Outcome.Success -> Outcome.Success(identity)
        }
    }

    override suspend fun listAll(linkId: String): List<DeviceIdentity> =
        store.entries.filter { it.key.linkId == linkId }.map { it.value }
}
