package cryptokit.crypto.api

/** HKDF-SHA256 key derivation. Per FR-008. */
interface KeyDerivation {
    /**
     * String-context overload — for the typical case where `info` is an ASCII / UTF-8 label
     * like `"config-key-v1"`. Encoded as UTF-8 internally.
     */
    suspend fun derive(ikm: ByteArray, salt: ByteArray, info: String, length: Int): ByteArray =
        derive(ikm, salt, info.encodeToByteArray(), length)

    /**
     * Byte-context overload — required for RFC 5869 KAT vectors which use binary `info`
     * outside the printable-ASCII range.
     *
     * @param ikm input keying material (e.g., shared secret from ECDH).
     * @param salt cryptographic salt (any byte string; can be empty).
     * @param info context info bytes (separates derived keys for different purposes).
     * @param length output length in bytes (typically 32 for an AEAD key).
     */
    suspend fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray
}
