package cryptokit.keys.fakes

import cryptokit.keys.api.DeviceId
import cryptokit.keys.api.internal.DeviceIdentity

/**
 * In-memory [DeviceIdentity] for tests. Fixed deviceId + injected keypair.
 *
 * Caller passes pre-generated X25519 keypair (test setup constructs one via the
 * real [family.crypto.libsodium.LibsodiumAsymmetricCrypto]). Each fake instance
 * represents one physical device.
 */
internal class FakeDeviceIdentity(
    private val deviceId: DeviceId,
    private val privKey: ByteArray,
    private val pubKey: ByteArray
) : DeviceIdentity {

    override suspend fun thisDeviceId(): DeviceId = deviceId
    override suspend fun myPubKey(): ByteArray = pubKey.copyOf()
    override suspend fun myPrivKey(): ByteArray = privKey.copyOf()
}
