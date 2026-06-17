package family.crypto.api

import family.crypto.api.values.Ciphertext

/**
 * Authenticated Encryption with Associated Data. Per FR-006 + Clarifications Q3 (nonce policy).
 *
 * **Nonce policy**: the nonce is generated internally by the adapter and embedded in the
 * returned [Ciphertext]. Callers MUST NOT supply or extract the nonce — doing so would risk
 * nonce reuse which destroys AEAD security. Use [aad] for any context that should be
 * authenticated but not encrypted.
 */
interface AeadCipher {

    /**
     * @param plaintext raw bytes to encrypt.
     * @param key 32-byte symmetric key (from [KeyDerivation] or [RandomSource]).
     * @param aad additional authenticated data (not encrypted but authenticated).
     * @return ciphertext envelope (nonce + ciphertext + MAC).
     * @throws family.crypto.exception.CryptoException.RandomSourceUnavailable if entropy fails.
     */
    suspend fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): Ciphertext

    /**
     * @throws family.crypto.exception.CryptoException.DecryptionFailed on MAC mismatch
     *   (corruption, tampering, wrong key).
     * @throws family.crypto.exception.CryptoException.MalformedCiphertext if envelope structure invalid.
     */
    suspend fun decrypt(
        ciphertext: Ciphertext,
        key: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray
}
