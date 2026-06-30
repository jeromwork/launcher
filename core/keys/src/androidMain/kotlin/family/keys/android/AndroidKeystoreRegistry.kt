package family.keys.android

import cryptokit.crypto.api.KeyDerivation
import family.keys.api.AuthIdentity
import family.keys.api.DerivedKey
import family.keys.api.KeyRegistry
import family.keys.api.Outcome
import family.keys.api.RootKeyError
import family.keys.api.RootKeyManager
import family.keys.api.StableId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android implementation of [KeyRegistry] (T632, FR-008).
 *
 * **Derivation**: `DerivedKey = HKDF-SHA256(rootKey, salt = stableId, info = purpose)`
 * per [KeyRegistry] KDoc. Root key material is obtained via [rootKeyManager]; HKDF
 * itself is delegated to [keyDerivation] (default impl is [cryptokit.crypto.libsodium.LibsodiumKeyDerivation]).
 *
 * **Storage model**: no separate persistence layer. Derivation is deterministic, so
 * a fresh process can reconstruct any `DerivedKey` by calling [derive] again. The
 * registry only keeps an **in-memory** cache (per-process) of materialized keys so
 * frequent callers (ConfigCipher2, Envelope flows) avoid recomputing HKDF on every
 * call. The cache is the only thing [wipeAll] touches — it does **not** wipe the
 * root key. Caller-orchestrated cascade per [RootKeyManager.forget] KDoc.
 *
 * **StrongBox**: handled transparently by the underlying [cryptokit.crypto.api.SecureKeyStore]
 * (see [cryptokit.crypto.SecureKeyStore.android.kt]). The registry itself does not
 * touch Android Keystore directly — root key wrapping is `:core:crypto`'s concern.
 *
 * **Thread safety**: [mutex] guards the per-stableId map mutations. Reads on hot
 * paths still need the lock (derived-key cache mutation), but HKDF is cheap once
 * the root key is loaded.
 *
 * **list() semantics**: returns purposes materialized **in this process so far**.
 * On a fresh process the list starts empty and grows on each [derive] call. This
 * is intentional — SC-012 wipe-correctness is about the live registry view, not
 * about historical persisted purpose audit.
 */
class AndroidKeystoreRegistry(
    private val rootKeyManager: RootKeyManager,
    private val keyDerivation: KeyDerivation
) : KeyRegistry {

    private val mutex = Mutex()
    private val cache: MutableMap<StableId, MutableMap<String, DerivedKey>> = mutableMapOf()

    override suspend fun derive(stableId: StableId, purpose: String): Outcome<DerivedKey, RootKeyError> {
        require(stableId.isNotEmpty()) { "stableId MUST not be empty (FR-031 isolation)" }
        require(purpose.isNotEmpty()) { "purpose MUST not be empty" }

        // Cache hit — return a fresh ByteArray copy so callers can wipe independently.
        mutex.withLock {
            cache[stableId]?.get(purpose)?.let { cached ->
                return Outcome.Success(DerivedKey(cached.bytes.copyOf()))
            }
        }

        // Cache miss — load root key, derive, cache, return.
        val identity = AuthIdentity(stableId, displayName = null, email = null)
        val rootKey = when (val rk = rootKeyManager.getOrCreate(identity)) {
            is Outcome.Success -> rk.value
            is Outcome.Failure -> return Outcome.Failure(rk.error)
        }

        return try {
            val derivedBytes = keyDerivation.derive(
                ikm = rootKey.bytes,
                salt = stableId.encodeToByteArray(),
                info = purpose.encodeToByteArray(),
                length = DERIVED_KEY_LEN
            )
            val derived = DerivedKey(derivedBytes)
            mutex.withLock {
                val byPurpose = cache.getOrPut(stableId) { mutableMapOf() }
                byPurpose[purpose] = DerivedKey(derivedBytes.copyOf())
            }
            Outcome.Success(derived)
        } catch (t: Throwable) {
            Outcome.Failure(RootKeyError.StorageFailure(t))
        }
    }

    override suspend fun wipeAll(stableId: StableId): Outcome<Unit, RootKeyError> {
        mutex.withLock {
            cache.remove(stableId)?.values?.forEach { it.wipe() }
        }
        return Outcome.Success(Unit)
    }

    override suspend fun list(stableId: StableId): Outcome<List<String>, RootKeyError> {
        return mutex.withLock {
            Outcome.Success(cache[stableId]?.keys?.toList().orEmpty())
        }
    }

    companion object {
        const val DERIVED_KEY_LEN: Int = 32
    }
}
