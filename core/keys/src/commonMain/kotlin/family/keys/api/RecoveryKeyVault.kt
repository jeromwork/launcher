package family.keys.api

/**
 * Storage для passphrase-wrapped root key (FR-008, FR-022, FR-024).
 *
 * **Backend** (production): Firestore по пути `users/{uid}/recovery-key`. Security
 * rules enforce `auth.uid == uid` (FR-009).
 *
 * **Backend** (test/dev): in-memory fake; `OwnServerRecoveryKeyVault` adapter
 * заменит Firestore variant когда мы переедем на свой сервер (СLAUDE.md rule 8,
 * docs/dev/server-roadmap.md SRV-RECOVERY-001).
 *
 * **App-agnostic** (FR-022): blob НЕ содержит package name / build version —
 * future multi-app cohabitation (S-2) переиспользует тот же vault namespace.
 *
 * TODO(future-spec V-2/V-3/P-10): cross-app root key sharing via broker pattern —
 * см. docs/product/future/multi-app-cohabitation.md.
 *
 * Per contracts/recovery-vault-v1.md.
 */
interface RecoveryKeyVault {
    /**
     * Возвращает blob для UID. Отсутствие → `VaultError.NotFound`.
     */
    suspend fun fetchVault(uid: String): Outcome<RecoveryVaultBlob, VaultError>

    /**
     * Перезаписывает blob (last-write-wins per current backend; transactional
     * conflict surfacing — backend-specific).
     */
    suspend fun storeVault(uid: String, blob: RecoveryVaultBlob): Outcome<Unit, VaultError>

    /**
     * Удаляет blob (Sign-Out cleanup или recovery reset).
     */
    suspend fun deleteVault(uid: String): Outcome<Unit, VaultError>
}
