package family.keys.fakes

import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.api.RootKey
import family.keys.api.RootKeyError
import family.keys.api.RootKeyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stateful in-memory [RootKeyManager] для тестов (FR-022, CLAUDE.md rule 6).
 *
 * **Deterministic**: `create()` генерирует 32-байтный ключ на основе XOR-chain от
 * `"root|$stableId"` — детерминированно, без реального libsodium.
 * `recover()` извлекает ключ из in-memory хранилища (нет реального Argon2id).
 *
 * **Stateful Flow**: [current] обновляется при [create], [recover], [forget].
 *
 * **Spec 018 compatibility**: [getOrCreate] и [wipe] также реализованы для backward compat.
 *
 * @see RootKeyManager
 * @see FakeKeyRegistry
 */
class FakeRootKeyManager : RootKeyManager {

    // stableId → root key bytes
    private val store: MutableMap<String, ByteArray> = mutableMapOf()
    private val passphraseStore: MutableMap<String, String> = mutableMapOf()
    private val _current = MutableStateFlow<RootKey?>(null)

    override val current: Flow<RootKey?> = _current.asStateFlow()

    // --- F-5 API ---

    override suspend fun create(identity: AuthIdentity): Outcome<RootKey, RootKeyError> {
        val key = RootKey(deterministicBytes("root", identity.stableId))
        store[identity.stableId] = key.bytes.copyOf()
        _current.value = key
        return Outcome.Success(key)
    }

    override suspend fun recover(
        identity: AuthIdentity,
        passphrase: CharArray
    ): Outcome<RootKey, RootKeyError> {
        val bytes = store[identity.stableId]
            ?: return Outcome.Failure(RootKeyError.RecoveryRequired)
        val expected = passphraseStore[identity.stableId]
        if (expected != null && !passphrase.concatToString().equals(expected)) {
            return Outcome.Failure(RootKeyError.WrongPassphrase)
        }
        val key = RootKey(bytes.copyOf())
        _current.value = key
        return Outcome.Success(key)
    }

    override suspend fun forget(identity: AuthIdentity): Outcome<Unit, RootKeyError> {
        store.remove(identity.stableId)
        passphraseStore.remove(identity.stableId)
        _current.value = null
        return Outcome.Success(Unit)
    }

    // --- Legacy spec 018 API ---

    override suspend fun getOrCreate(identity: AuthIdentity): Outcome<RootKey, RootKeyError> {
        val bytes = store.getOrPut(identity.stableId) {
            deterministicBytes("root", identity.stableId)
        }
        val key = RootKey(bytes.copyOf())
        _current.value = key
        return Outcome.Success(key)
    }

    override suspend fun wipe(identity: AuthIdentity): Outcome<Unit, RootKeyError> {
        store.remove(identity.stableId)
        passphraseStore.remove(identity.stableId)
        _current.value = null
        return Outcome.Success(Unit)
    }

    // --- test helpers ---

    /** Test introspection: есть ли ключ для данного stableId. */
    fun hasKey(stableId: String): Boolean = stableId in store

    /** Test hook: seed конкретный ключ для recovery сценария. */
    fun seedKey(stableId: String, bytes: ByteArray, passphrase: String? = null) {
        store[stableId] = bytes.copyOf()
        if (passphrase != null) {
            passphraseStore[stableId] = passphrase
        }
    }

    private fun deterministicBytes(prefix: String, stableId: String): ByteArray {
        val input = "$prefix|$stableId"
        // Simple deterministic bytes: XOR chain — works in commonTest без java.security.
        val bytes = ByteArray(RootKey.SIZE)
        val src = input.encodeToByteArray()
        for (i in bytes.indices) {
            bytes[i] = (src[i % src.size].toInt() xor (i * 31)).toByte()
        }
        return bytes
    }
}
