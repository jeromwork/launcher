package com.launcher.api.crypto

import com.launcher.api.result.Outcome

// AEAD symmetric cipher port. Adapter (Phase 3) — XChaCha20-Poly1305 через libsodium.
// Fake adapter (Phase 2) — XOR-stub.
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
