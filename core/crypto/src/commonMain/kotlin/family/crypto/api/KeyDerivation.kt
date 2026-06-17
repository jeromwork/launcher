package family.crypto.api

/** HKDF-SHA256 key derivation. Per FR-008. */
interface KeyDerivation {
    /**
     * @param ikm input keying material (e.g., shared secret from ECDH).
     * @param salt cryptographic salt (any byte string; can be empty).
     * @param info context info — separates derived keys for different purposes
     *   (e.g., `"config-key-v1"`, `"launcher-recovery-aead-v1"`).
     * @param length output length in bytes (typically 32 for an AEAD key).
     */
    suspend fun derive(ikm: ByteArray, salt: ByteArray, info: String, length: Int): ByteArray
}
