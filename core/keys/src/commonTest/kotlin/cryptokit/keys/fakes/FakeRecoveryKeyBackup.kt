package cryptokit.keys.fakes

import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecoveryKeyBackup
import cryptokit.keys.api.RecoveryKeyBackupBlob
import cryptokit.keys.api.BackupError

/**
 * In-memory [RecoveryKeyBackup] для тестов (CLAUDE.md rule 6).
 *
 * Хранит `Map<uid, RecoveryKeyBackupBlob>`. Все операции synchronous, без сетевых
 * задержек. Подходит для contract tests и integration tests где нужно симулировать
 * cross-device recovery без Firestore Emulator.
 */
class FakeRecoveryKeyBackup : RecoveryKeyBackup {

    private val backups = mutableMapOf<String, RecoveryKeyBackupBlob>()

    /** Test introspection: количество blob'ов в хранилище. */
    fun size(): Int = backups.size

    /** Test introspection: present check без BackupError wrapping. */
    fun has(uid: String): Boolean = uid in backups

    /** Test hook: pre-seed backup от другого device'а (cross-device recovery scenario). */
    fun seed(uid: String, blob: RecoveryKeyBackupBlob) {
        backups[uid] = blob
    }

    override suspend fun fetchBlob(uid: String): Outcome<RecoveryKeyBackupBlob, BackupError> {
        val blob = backups[uid] ?: return Outcome.Failure(BackupError.NotFound)
        return Outcome.Success(blob)
    }

    override suspend fun uploadBlob(uid: String, blob: RecoveryKeyBackupBlob): Outcome<Unit, BackupError> {
        backups[uid] = blob
        return Outcome.Success(Unit)
    }

    override suspend fun deleteBlob(uid: String): Outcome<Unit, BackupError> {
        backups.remove(uid)
        return Outcome.Success(Unit)
    }
}
