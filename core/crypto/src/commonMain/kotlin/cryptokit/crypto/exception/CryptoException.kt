package cryptokit.crypto.exception

/** Sealed hierarchy of crypto failures. Per data-model.md §"Exception hierarchy" (spec 016). */
sealed class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** AEAD decryption failed — MAC mismatch, tampering, or wrong key. */
    class DecryptionFailed(message: String = "AEAD decryption MAC mismatch") : CryptoException(message)

    /** Ciphertext envelope structure invalid (e.g., shorter than nonce+mac). */
    class MalformedCiphertext(message: String) : CryptoException(message)

    /** Invalid public key (low-order point, point-at-infinity, malformed). */
    class InvalidPublicKey(message: String) : CryptoException(message)

    /** Random source unavailable (e.g., entropy pool empty). */
    class RandomSourceUnavailable(cause: Throwable? = null) :
        CryptoException("Random source unavailable", cause)

    /** Android Keystore / iOS Keychain not available. */
    class KeystoreUnavailable(message: String, cause: Throwable? = null) :
        CryptoException(message, cause)

    /** Keystore alias was invalidated externally (e.g., Xiaomi MIUI cleanup, biometry changed). */
    class KeystoreInvalidated(message: String, cause: Throwable? = null) :
        CryptoException(message, cause)

    /** KeyBlob has schemaVersion higher than current code can read. */
    class UnsupportedSchemaVersion(val found: Int, val known: Int) :
        CryptoException("Cannot read KeyBlob with schemaVersion=$found (max known: $known)")

    /** KeyBlob deserialization failed (corrupt file, malformed JSON). */
    class KeyBlobDeserializationFailed(message: String, cause: Throwable? = null) :
        CryptoException(message, cause)

    /** Wycheproof rejection (e.g., low-order X25519 point detected). */
    class WycheproofRejection(message: String) : CryptoException(message)

    /** Caller attempted to encrypt twice with the same key+nonce (detected by Fake or property test). */
    class NonceReuseDetected(message: String) : CryptoException(message)

    /** iOS adapter not yet implemented. */
    class NotImplementedOnIos(message: String) : CryptoException(message)

    // ---- TASK-51 broad-category hierarchy (data-model §1 / FR-018) ----
    // Used by pairing-side adapters rewritten in Phase 6 to wrap vendor exceptions
    // into uniform throws-pattern. Co-exist with granular spec-016 subclasses above.

    /** AEAD encrypt/decrypt failures (Poly1305 MAC mismatch, invalid ciphertext layout). */
    class AeadException(message: String, cause: Throwable? = null) :
        CryptoException(message, cause)

    /** SecureKeyStore failures (TEE unavailable, key invalidated, biometry change). */
    class KeyStoreException(message: String, cause: Throwable? = null) :
        CryptoException(message, cause)

    /** KDF / HKDF / X25519 / Ed25519 derivation failures (invalid salt, weak input). */
    class KeyDerivationException(message: String, cause: Throwable? = null) :
        CryptoException(message, cause)

    /** JNI link errors (UnsatisfiedLinkError, missing libsodium native symbols). */
    class NativeLinkException(message: String, cause: Throwable? = null) :
        CryptoException(message, cause)

    /** Wire-format read/write failures (kotlinx.serialization wrapping). */
    class SerializationException(message: String, cause: Throwable? = null) :
        CryptoException(message, cause)
}
