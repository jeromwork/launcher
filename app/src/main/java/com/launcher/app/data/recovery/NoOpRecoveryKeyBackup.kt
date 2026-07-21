package com.launcher.app.data.recovery

import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackup
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.BackupError

/**
 * [RecoveryKeyBackup] для non-GMS devices (T046, FR-028).
 *
 * Все операции → [BackupError.AuthExpired]. UI должен скрыть recovery flow
 * на таких устройствах.
 *
 * Используется через flavor logic — в `mockBackend` flavor (без Firebase)
 * это единственный binding; в `realBackend` flavor — fallback когда GMS
 * недоступен (Huawei).
 */
class NoOpRecoveryKeyBackup : RecoveryKeyBackup {
    override suspend fun fetchBlob(uid: String): Outcome<RecoveryKeyBackupBlob, BackupError> =
        Outcome.Failure(BackupError.AuthExpired)

    override suspend fun uploadBlob(uid: String, blob: RecoveryKeyBackupBlob): Outcome<Unit, BackupError> =
        Outcome.Failure(BackupError.AuthExpired)

    override suspend fun deleteBlob(uid: String): Outcome<Unit, BackupError> =
        Outcome.Failure(BackupError.AuthExpired)
}
