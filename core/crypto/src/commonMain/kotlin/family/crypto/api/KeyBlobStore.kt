package family.crypto.api

import family.crypto.api.values.KeyId
import family.crypto.api.values.WrappedKeyMaterial
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistence port for wrapped key material — the seam TASK-141 introduced so that
 * [SecureKeyStore] (crypto) stops owning the on-disk format. Crypto wraps the secret
 * and hands the opaque [WrappedKeyMaterial] here; the adapter above crypto decides the
 * wire version and serialization (the `KeyBlob` JSON lives in the adapter, not in
 * `:core:crypto`, per rule 1). Crypto never learns what the persisted bytes look like.
 *
 * Production adapter: `FileKeyBlobStore` (:core androidMain) — `<filesDir>/keys/<id>.blob`.
 * Test / dev adapter: [InMemoryKeyBlobStore] below.
 */
interface KeyBlobStore {
    /** Persist [material] under [keyId], overwriting any existing entry. */
    suspend fun write(keyId: KeyId, material: WrappedKeyMaterial)

    /** @return the stored material, or null if [keyId] has none. */
    suspend fun read(keyId: KeyId): WrappedKeyMaterial?

    /** Idempotent — does not throw if [keyId] has no entry. */
    suspend fun delete(keyId: KeyId)
}

/**
 * In-memory [KeyBlobStore] — mock-first (CLAUDE.md §6). Holds already-wrapped material,
 * so it never touches plaintext and needs no serialization. Used by tests and JVM/dev
 * paths; production uses the file-backed adapter.
 */
class InMemoryKeyBlobStore : KeyBlobStore {
    private val store = HashMap<String, WrappedKeyMaterial>()
    private val mutex = Mutex()

    override suspend fun write(keyId: KeyId, material: WrappedKeyMaterial) {
        mutex.withLock { store[keyId.raw] = material }
    }

    override suspend fun read(keyId: KeyId): WrappedKeyMaterial? =
        mutex.withLock { store[keyId.raw] }

    override suspend fun delete(keyId: KeyId) {
        mutex.withLock { store.remove(keyId.raw) }
    }
}
