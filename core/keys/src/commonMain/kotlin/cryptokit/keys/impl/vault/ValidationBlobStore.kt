package cryptokit.keys.impl.vault

/**
 * Persistent store for the small "known-plaintext" blob a passphrase-style [cryptokit.keys.api.vault.RecoveryStrategy]
 * uses to answer "did the user type the right passphrase?" *before* touching any real data
 * (FR-006b, Session 7 Q-D D1).
 *
 * Deliberately tiny surface — a single opaque byte blob keyed by `identityHint`. Storage tier
 * is opaque to the strategy:
 *  * `AndroidKeyVault` wires this to `EncryptedSharedPreferences`.
 *  * `FakeKeyVault` uses an in-memory map.
 *
 * The blob is a full [cryptokit.crypto.api.values.Ciphertext] envelope (XChaCha20-Poly1305,
 * key = derived root, plaintext = `"vault-init-v1"`).
 */
interface ValidationBlobStore {
    /** Read the currently stored validation blob for [hintKey], or `null` if none exists yet. */
    suspend fun read(hintKey: String): ByteArray?

    /** Overwrite the stored validation blob for [hintKey] with [blob]. */
    suspend fun write(hintKey: String, blob: ByteArray)

    /** Remove any stored validation blob for [hintKey]. Idempotent. */
    suspend fun clear(hintKey: String)
}

/** In-memory implementation — the default for `FakeKeyVault` and unit tests. */
class InMemoryValidationBlobStore : ValidationBlobStore {
    private val map = mutableMapOf<String, ByteArray>()
    override suspend fun read(hintKey: String): ByteArray? = map[hintKey]?.copyOf()
    override suspend fun write(hintKey: String, blob: ByteArray) { map[hintKey] = blob.copyOf() }
    override suspend fun clear(hintKey: String) { map.remove(hintKey) }
}
