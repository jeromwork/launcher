package family.keys.api

/**
 * Storage для passphrase-wrapped root key (FR-008, FR-022, FR-024).
 *
 * **Backend** (production): Firestore по пути `users/{uid}/recovery-key`. Security
 * rules enforce `auth.uid == uid` (FR-009).
 *
 * **Backend** (test/dev): in-memory fake; `OwnServerRecoveryKeyBackup` adapter
 * заменит Firestore variant когда мы переедем на свой сервер (СLAUDE.md rule 8,
 * docs/dev/server-roadmap.md SRV-RECOVERY-001).
 *
 * **App-agnostic** (FR-022): blob НЕ содержит package name / build version —
 * future multi-app cohabitation (S-2) переиспользует тот же backup namespace.
 *
 * TODO(future-spec V-2/V-3/P-10): cross-app root key sharing via broker pattern —
 * см. docs/product/future/multi-app-cohabitation.md.
 *
 * Per specs/task-6-root-key-hierarchy-recovery/contracts/recovery-key-backup-v1.md.
 */
interface RecoveryKeyBackup {
    /**
     * Возвращает blob для UID. Отсутствие → `VaultError.NotFound`.
     */
    suspend fun fetchBlob(uid: String): Outcome<RecoveryKeyBackupBlob, VaultError>

    /**
     * Перезаписывает blob (last-write-wins per current backend; transactional
     * conflict surfacing — backend-specific).
     */
    suspend fun uploadBlob(uid: String, blob: RecoveryKeyBackupBlob): Outcome<Unit, VaultError>

    /**
     * Удаляет blob (Sign-Out cleanup или recovery reset).
     */
    suspend fun deleteBlob(uid: String): Outcome<Unit, VaultError>
}
