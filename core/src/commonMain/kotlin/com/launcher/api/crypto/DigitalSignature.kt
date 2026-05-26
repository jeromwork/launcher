package com.launcher.api.crypto

import com.launcher.api.result.Outcome

// Ed25519 signature port. 011 uses для подписи Pub publication payload (FR-006).
// Future consumers — spec 013 (symmetric pairing), TBD-Jitsi (room key signing),
// TBD-Vendor (JWT/HMAC).
interface DigitalSignature {
    fun generateEd25519Pair(alias: String): DeviceSigningKeyPair

    fun sign(data: ByteArray, ownPair: DeviceSigningKeyPair): ByteArray

    fun verify(
        data: ByteArray,
        signature: ByteArray,
        pubKey: SigningPublicKey,
    ): Outcome<Unit, CryptoError>
}
