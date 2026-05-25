package com.launcher.api.crypto

import com.launcher.api.result.Outcome

// /links/{linkId}/devices/{deviceId} repository. fetchPeer MUST verify Ed25519
// signature над signedPayloadBytes ДО возврата identity. SignatureVerifyFailed
// при tamper / stale timestamp — никакого fallback.
interface DeviceIdentityRepository {
    suspend fun publishOwn(linkId: String, identity: DeviceIdentity): Outcome<Unit, CryptoError>

    suspend fun fetchPeer(linkId: String, peerDeviceId: DeviceId): Outcome<DeviceIdentity, CryptoError>

    suspend fun listAll(linkId: String): List<DeviceIdentity>
}
