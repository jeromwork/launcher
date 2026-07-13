package cryptokit.keys.api

/**
 * Ошибки backup-операций для F-5 key hierarchy (FR-004, data-model.md §10).
 *
 * **Lifecycle**: используется как тип ошибки в [RecoveryKeyBackup]. Заменил [VaultError]
 * в рамках T609-migration.
 *
 * **Mapping из HTTP** (workers/backup/):
 *  - 4xx network + timeout → [NetworkUnavailable]
 *  - 401 → [AuthExpired] (JWT expired / missing)
 *  - 403 → [AuthExpired] (auth.uid != stableId owner)
 *  - 429 → [ServerQuotaExceeded]
 *  - 409 → [Conflict] (idempotency key body mismatch)
 *  - decode fail или missing required field → [Malformed]
 *  - schemaVersion > MAX_SUPPORTED_SCHEMA_VERSION → [UnsupportedSchema]
 *
 * Note (C3): [Malformed] and [NotFound] are included beyond the base T609 specification
 * in `tasks.md` as they are practically required by codec validation and fetch operations.
 *
 * @see RecoveryKeyBackup
 */
sealed class BackupError {
    /**
     * Сеть недоступна или запрос timed out. Retry с exponential back-off.
     * @param cause Оригинальное IO exception (для диагностики).
     */
    data class NetworkUnavailable(val cause: Throwable? = null) : BackupError()

    /**
     * JWT token истёк или отсутствует. Требуется re-authentication через F-4.
     */
    object AuthExpired : BackupError() {
        override fun toString(): String = "BackupError.AuthExpired"
    }

    /**
     * Worker вернул 429 Too Many Requests. Клиент должен соблюдать `Retry-After` header.
     * @param retryAfterSeconds Секунды ожидания из Retry-After header, если доступен.
     */
    data class ServerQuotaExceeded(val retryAfterSeconds: Int? = null) : BackupError()

    /**
     * Idempotency key конфликт: тот же ключ + другое тело запроса (не совпадает с
     * предыдущим). Caller должен использовать новый idempotency key.
     */
    object Conflict : BackupError() {
        override fun toString(): String = "BackupError.Conflict"
    }

    /**
     * Blob shape не соответствует схеме — corruption или unsupported field types.
     * Не путать с [UnsupportedSchema] (schemaVersion too new).
     */
    object Malformed : BackupError() {
        override fun toString(): String = "BackupError.Malformed"
    }

    /**
     * `schemaVersion` > [RecoveryKeyBackupBlob.SCHEMA_VERSION] — blob создан новой версией
     * приложения. UI должен предложить обновить приложение.
     *
     * @param version Версия схемы из blob.
     */
    data class UnsupportedSchema(val version: Int) : BackupError()

    /**
     * `fetched.schemaVersion < lastSeenVersion` (TOLU mitigation, H-2).
     */
    object SchemaDowngradeDetected : BackupError() {
        override fun toString(): String = "BackupError.SchemaDowngradeDetected"
    }

    /**
     * Backup не найден для данного stableId — новый пользователь или после wipe.
     */
    object NotFound : BackupError() {
        override fun toString(): String = "BackupError.NotFound"
    }
}
