package com.launcher.api.crypto

import com.launcher.api.result.Outcome

/**
 * Ed25519 signature port. 011 uses для подписи Pub publication payload (FR-006).
 * Future consumers — spec 013 (symmetric pairing), TBD-Jitsi (room key signing),
 * TBD-Vendor (JWT/HMAC).
 *
 * ⚠️ **DO NOT use directly from UI / business logic / feature modules.**
 * Use `PrivateMediaUploader` / `PrivateMediaResolver` facades (spec 012) instead.
 * See [docs/dev/private-media-architecture.md].
 *
 * Rationale: Article XI §8 (Reuse before invention) — единая точка для
 * media encryption pipeline.
 */
interface DigitalSignature {
    fun generateEd25519Pair(alias: String): DeviceSigningKeyPair

    fun sign(data: ByteArray, ownPair: DeviceSigningKeyPair): ByteArray

    fun verify(
        data: ByteArray,
        signature: ByteArray,
        pubKey: SigningPublicKey,
    ): Outcome<Unit, CryptoError>
}
