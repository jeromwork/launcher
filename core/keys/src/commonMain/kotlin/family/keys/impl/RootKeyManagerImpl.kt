package family.keys.impl

import family.crypto.api.AeadCipher
import family.crypto.api.RandomSource
import family.crypto.api.SecureKeyStore
import family.crypto.api.values.Ciphertext
import family.crypto.api.values.KeyId
import family.crypto.exception.CryptoException
import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.api.RootKey
import family.keys.api.RootKeyError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [family.keys.api.RootKeyManager] implementation.
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
 * cached instance. НЕ удаляет Firestore vault (RecoveryKeyVault.deleteVault —
 * отдельный вызов).
 */
class RootKeyManagerImpl(
    private val secureKeyStore: SecureKeyStore,
    private val random: RandomSource,
    @Suppress("unused") private val aead: AeadCipher
) : family.keys.api.RootKeyManager {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, RootKey>()

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
