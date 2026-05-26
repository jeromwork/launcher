package com.launcher.api.crypto

import com.launcher.api.result.Outcome

/**
 * AEAD symmetric cipher port. Adapter (Phase 3) — XChaCha20-Poly1305 через libsodium.
 * Fake adapter (Phase 2) — XOR-stub.
 *
 * ⚠️ **DO NOT use directly from UI / business logic / feature modules.**
 * Use `PrivateMediaUploader` / `PrivateMediaResolver` facades (spec 012) instead.
 * See [docs/dev/private-media-architecture.md].
 *
 * Rationale (Article XI §8 — Reuse before invention, спек 012 clarify Q3):
 * Direct use из UI обходит media pipeline conventions (encryption + reference counting +
 * LocalMediaStore caching) и приводит к утечке cryptographic concerns в UI.
 */
interface AeadCipher {
    fun encrypt(
        plaintext: ByteArray,
        key: ContentEncryptionKey,
        nonce: ByteArray,
        aad: ByteArray,
    ): ByteArray

    fun decrypt(
        ciphertext: ByteArray,
        key: ContentEncryptionKey,
        nonce: ByteArray,
        aad: ByteArray,
    ): Outcome<ByteArray, CryptoError>

    fun randomNonce(): ByteArray

    fun generateCEK(): ContentEncryptionKey
}
