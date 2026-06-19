package family.keys.fakes

import family.keys.api.Outcome
import family.keys.api.RecoveryKeyVault
import family.keys.api.RecoveryVaultBlob
import family.keys.api.VaultError

/**
 * In-memory [RecoveryKeyVault] для тестов (CLAUDE.md rule 6).
 *
 * Хранит `Map<uid, RecoveryVaultBlob>`. Все операции synchronous, без сетевых
 * задержек. Подходит для contract tests и integration tests где нужно симулировать
 * cross-device recovery без Firestore Emulator.
 */
class FakeRecoveryKeyVault : RecoveryKeyVault {

    private val vaults = mutableMapOf<String, RecoveryVaultBlob>()

    /** Test introspection: количество vault'ов в хранилище. */
    fun size(): Int = vaults.size

    /** Test introspection: present check без VaultError wrapping. */
    fun has(uid: String): Boolean = uid in vaults

    /** Test hook: pre-seed vault от другого device'а (cross-device recovery scenario). */
    fun seed(uid: String, blob: RecoveryVaultBlob) {
        vaults[uid] = blob
    }

    override suspend fun fetchVault(uid: String): Outcome<RecoveryVaultBlob, VaultError> {
        val blob = vaults[uid] ?: return Outcome.Failure(VaultError.NotFound)
        return Outcome.Success(blob)
    }

    override suspend fun storeVault(uid: String, blob: RecoveryVaultBlob): Outcome<Unit, VaultError> {
        vaults[uid] = blob
        return Outcome.Success(Unit)
    }

    override suspend fun deleteVault(uid: String): Outcome<Unit, VaultError> {
        vaults.remove(uid)
        return Outcome.Success(Unit)
    }
}
