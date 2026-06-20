package com.launcher.app.data.recovery

import family.keys.api.Outcome
import family.keys.api.RecoveryKeyVault
import family.keys.api.RecoveryVaultBlob
import family.keys.api.VaultError

/**
 * [RecoveryKeyVault] для non-GMS devices (T046, FR-028).
 *
 * Все операции → [VaultError.Unauthorized]. UI должен скрыть recovery flow
 * на таких устройствах.
 *
 * Используется через flavor logic — в `mockBackend` flavor (без Firebase)
 * это единственный binding; в `realBackend` flavor — fallback когда GMS
 * недоступен (Huawei).
 */
class NoOpRecoveryKeyVault : RecoveryKeyVault {
    override suspend fun fetchVault(uid: String): Outcome<RecoveryVaultBlob, VaultError> =
        Outcome.Failure(VaultError.Unauthorized)

    override suspend fun storeVault(uid: String, blob: RecoveryVaultBlob): Outcome<Unit, VaultError> =
        Outcome.Failure(VaultError.Unauthorized)

    override suspend fun deleteVault(uid: String): Outcome<Unit, VaultError> =
        Outcome.Failure(VaultError.Unauthorized)
}
