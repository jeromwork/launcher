package cryptokit.pairing.api

// /links/{linkId}/devices/{deviceId} repository. fetchPeer MUST verify Ed25519
// signature над signedPayloadBytes ДО возврата identity. SignatureVerifyFailed
// при tamper / stale timestamp — никакого fallback.
//
// Signatures use uniform `throws CryptoException` pattern (TASK-51 FR-009).
// Previous Outcome<T, CryptoError> shape removed.
interface DeviceIdentityRepository {
    /** @throws cryptokit.crypto.exception.CryptoException on serialization / network failure. */
    suspend fun publishOwn(linkId: String, identity: DeviceIdentity)

    /** @throws cryptokit.crypto.exception.CryptoException on signature verify fail or document missing. */
    suspend fun fetchPeer(linkId: String, peerDeviceId: DeviceId): DeviceIdentity

    suspend fun listAll(linkId: String): List<DeviceIdentity>
}
