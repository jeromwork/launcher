package cryptokit.crypto.api

import cryptokit.crypto.api.values.KeyId

/**
 * Persistent secret storage protected by platform-specific TEE.
 * Per FR-010 + Clarifications Q1 (iOS stub-screamer policy).
 *
 * Actual implementations:
 *  • `androidMain` — Android Keystore wrap pattern (TEE-backed AES wraps the raw bytes).
 *  • `jvmMain` — in-memory HashMap (TEST-only, NOT for production).
 *  • `iosMain` — stub-screamer; throws [cryptokit.crypto.exception.CryptoException.NotImplementedOnIos]
 *    on every call. Replaced when V-1 (iOS Admin Preset) spec ships.
 */
expect class SecureKeyStore(context: KeyStoreContext) {

    /**
     * Store a private-key secret under [keyId]. On Android, secret is wrapped by an
     * AES key in Android Keystore (TEE) before being persisted to disk.
     *
     * @throws cryptokit.crypto.exception.CryptoException.KeystoreUnavailable if TEE not available.
     * @throws cryptokit.crypto.exception.CryptoException.KeystoreInvalidated if the wrap-key
     *   alias was removed externally (Xiaomi MIUI cleanup, biometry change).
     */
    suspend fun store(keyId: KeyId, secret: ByteArray)

    /**
     * @return previously-stored secret bytes, or null if [keyId] not found
     *   (or device wiped / app data cleared).
     */
    suspend fun load(keyId: KeyId): ByteArray?

    /** Idempotent — does not throw if [keyId] not found. */
    suspend fun delete(keyId: KeyId)
}
