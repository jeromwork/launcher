package cryptokit.keys.impl

import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.crypto.api.values.Ciphertext
import cryptokit.crypto.api.values.KeyId
import cryptokit.crypto.exception.CryptoException
import cryptokit.keys.api.AuthIdentity
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RootKey
import cryptokit.keys.api.RootKeyError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [cryptokit.keys.api.RootKeyManager] implementation.
 *
 * **Local-only paths**:
 *  • `getOrCreate(identity)` — generate если нет, иначе load.
 *  • НЕ обрабатывает recovery flow (Phase 4). Recovery extends этот класс через
 *    optional vault-fallback path в Phase 4.
 *
 * **Storage**: SecureKeyStore слот = `root-key::{uid}`. Это raw 32-байтовый
 * root key material (на Android он сам wrap'нут TEE-backed AES Keystore; на JVM
 * test — in-memory plaintext).
 *
 * **In-process cache**: после первого getOrCreate keep RootKey instance в map
 * `uid → RootKey` чтобы избежать частых Keystore round-trips. Сейчас простой
 * `MutableMap`; thread-safety через [mutex].
 *
 * **Wipe semantics**: `wipe(identity)` удаляет из SecureKeyStore + обнуляет
 * cached instance. НЕ удаляет Firestore backup (RecoveryKeyBackup.deleteBlob —
 * отдельный вызов).
 */
class RootKeyManagerImpl(
    private val secureKeyStore: SecureKeyStore,
    private val random: RandomSource,
    @Suppress("unused") private val aead: AeadCipher
) : cryptokit.keys.api.RootKeyManager {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, RootKey>()
    private val _current = MutableStateFlow<RootKey?>(null)

    /**
     * Recovery delegate, set once at DI bootstrap via [bindRecoveryDelegate].
     * Indirection breaks the circular construction between this class and
     * [cryptokit.keys.impl.RecoveryFlow] (RecoveryFlow takes a `RootKeyManagerImpl`
     * in its constructor; if this class also took a RecoveryFlow, neither could
     * be built first). Kept package-internal so only the DI wiring can rebind.
     */
    @Volatile private var recoveryDelegate: RecoveryDelegate? = null

    /**
     * DI hook (T633): wire a [RecoveryFlow]-backed delegate after both
     * RootKeyManagerImpl and RecoveryFlow have been constructed. Calling twice
     * replaces the previous binding (last-writer-wins) — DI should call this
     * exactly once per app process.
     */
    fun bindRecoveryDelegate(delegate: RecoveryDelegate) {
        this.recoveryDelegate = delegate
    }

    // --- F-5 API (T613, D3) ---

    override val current: Flow<RootKey?> = _current.asStateFlow()

    override suspend fun create(identity: AuthIdentity): Outcome<RootKey, RootKeyError> {
        // Delegate to getOrCreate — F-5 `create()` semantics are «generate if
        // absent, return current if present», identical to spec 018's
        // getOrCreate(). The distinction in [cryptokit.keys.api.RootKeyManager]
        // is documentation-level (call sites that mean «I expect first-time
        // creation»), not algorithmic.
        val result = getOrCreate(identity)
        if (result is Outcome.Success) _current.value = result.value
        return result
    }

    override suspend fun recover(identity: AuthIdentity, passphrase: CharArray): Outcome<RootKey, RootKeyError> {
        // T633: delegate to RecoveryFlow when bound. Passphrase wipe is the
        // delegate's responsibility (RecoveryFlow.performRecovery owns the
        // CharArray once it crosses this boundary, per FR-013).
        val delegate = recoveryDelegate
            ?: return Outcome.Failure(RootKeyError.RecoveryRequired)
        val result = delegate.recover(identity, passphrase)
        if (result is Outcome.Success) _current.value = result.value
        return result
    }

    override suspend fun forget(identity: AuthIdentity): Outcome<Unit, RootKeyError> {
        val result = wipe(identity)
        if (result is Outcome.Success) _current.value = null
        return result
    }

    override suspend fun getOrCreate(identity: AuthIdentity): Outcome<RootKey, RootKeyError> {
        require(identity.stableId.isNotEmpty()) {
            "AuthIdentity.stableId must not be empty (H-4 cross-UID alias formation guard)"
        }
        return mutex.withLock {
            cache[identity.stableId]?.let { return@withLock Outcome.Success(it) }

            val keyId = keyIdFor(identity.stableId)
            try {
                val existing = secureKeyStore.load(keyId)
                if (existing != null) {
                    if (existing.size != RootKey.SIZE) {
                        // Corrupted blob — treat as missing, force recovery.
                        return@withLock Outcome.Failure(RootKeyError.RecoveryRequired)
                    }
                    val rk = RootKey(existing.copyOf())
                    cache[identity.stableId] = rk
                    return@withLock Outcome.Success(rk)
                }
                // Не было — генерируем.
                val fresh = random.nextBytes(RootKey.SIZE)
                secureKeyStore.store(keyId, fresh)
                val rk = RootKey(fresh.copyOf())
                cache[identity.stableId] = rk
                Outcome.Success(rk)
            } catch (e: CryptoException.KeystoreInvalidated) {
                Outcome.Failure(RootKeyError.KeystoreInvalidated)
            } catch (t: Throwable) {
                Outcome.Failure(RootKeyError.StorageFailure(t))
            }
        }
    }

    override suspend fun wipe(identity: AuthIdentity): Outcome<Unit, RootKeyError> {
        return mutex.withLock {
            try {
                cache.remove(identity.stableId)?.wipe()
                secureKeyStore.delete(keyIdFor(identity.stableId))
                Outcome.Success(Unit)
            } catch (t: Throwable) {
                Outcome.Failure(RootKeyError.StorageFailure(t))
            }
        }
    }

    /**
     * Internal hook для recovery flow (Phase 4 RootKeyManagerImpl extension):
     * напрямую инжектит recovered root в local Keystore + cache.
     *
     * Не публичный API — вызывается только из recovery flow.
     */
    internal suspend fun seedFromRecovery(identity: AuthIdentity, recoveredRoot: ByteArray) {
        require(recoveredRoot.size == RootKey.SIZE)
        require(identity.stableId.isNotEmpty())
        mutex.withLock {
            secureKeyStore.store(keyIdFor(identity.stableId), recoveredRoot)
            val rk = RootKey(recoveredRoot.copyOf())
            cache[identity.stableId]?.wipe()
            cache[identity.stableId] = rk
        }
    }

    /**
     * KeyId под F-CRYPTO `config-` namespace. UID нормализуется в lowercase
     * kebab-safe form (non-alphanumeric → '-'); это сохраняет identity isolation
     * (FR-031) при условии stableId distinct lowercase representations.
     */
    private fun keyIdFor(uid: String): KeyId = KeyId("config-root-${normalizeUid(uid)}")

    private fun normalizeUid(uid: String): String =
        uid.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
            .replace(Regex("-+"), "-").trim('-')

    companion object {
        const val NAMESPACE_PREFIX: String = "config-root"
    }
}

/**
 * Indirection boundary between [RootKeyManagerImpl.recover] and the recovery
 * implementation (currently [cryptokit.keys.impl.RecoveryFlow.performRecovery]).
 *
 * Exists only to break the circular construction described on
 * [RootKeyManagerImpl.recoveryDelegate]. App-side DI provides an adapter that
 * forwards into RecoveryFlow and maps [cryptokit.keys.api.RecoveryError] →
 * [cryptokit.keys.api.RootKeyError].
 *
 * Not meant to be exposed beyond the `:core:keys` impl layer — call sites
 * should depend on [cryptokit.keys.api.RootKeyManager] or [RecoveryFlow], not on
 * this interface.
 */
interface RecoveryDelegate {
    suspend fun recover(identity: AuthIdentity, passphrase: CharArray): Outcome<RootKey, RootKeyError>
}
