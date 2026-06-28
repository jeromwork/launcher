package com.launcher.app.data.recovery

import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackup
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.VaultError

/**
 * [RecoveryKeyBackup] для non-GMS devices (T046, FR-028).
 *
 * Все операции → [VaultError.Unauthorized]. UI должен скрыть recovery flow
 * на таких устройствах.
 *
 * Используется через flavor logic — в `mockBackend` flavor (без Firebase)
 * это единственный binding; в `realBackend` flavor — fallback когда GMS
 * недоступен (Huawei).
 */
class NoOpRecoveryKeyBackup : RecoveryKeyBackup {
    override suspend fun fetchBlob(uid: String): Outcome<RecoveryKeyBackupBlob, VaultError> =
        Outcome.Failure(VaultError.Unauthorized)

    override suspend fun uploadBlob(uid: String, blob: RecoveryKeyBackupBlob): Outcome<Unit, VaultError> =
        Outcome.Failure(VaultError.Unauthorized)

    override suspend fun deleteBlob(uid: String): Outcome<Unit, VaultError> =
        Outcome.Failure(VaultError.Unauthorized)
}
