package family.keys.api

/**
 * Ошибки [RootKeyManager] операций (FR-003, FR-025).
 *
 * **Legacy cases (spec 018)** — сохранены для backward compatibility:
 *  • [KeystoreInvalidated] — hardware-backed key инвалидирован OS update / biometric
 *    enrollment change. Trigger recovery flow.
 *  • [RecoveryRequired] — Keystore пуст для этой identity; нужен passphrase recovery.
 *  • [StorageFailure] — IO error при чтении/записи wrapped root key.
 *
 * **F-5 cases** (добавлены per D2 reconciliation, data-model.md §9):
 *  • [WrongPassphrase] — пароль не совпадает при Argon2id unwrap (recovery path).
 *  • [CorruptedBlob] — blob структурно невалиден (decode fail или byte-level corruption).
 *  • [NoIdentity] — identity отсутствует (stableId не присвоен — init-claim не вызывался).
 */
sealed class RootKeyError {
    // --- Legacy (spec 018) ---
    object KeystoreInvalidated : RootKeyError()
    object RecoveryRequired : RootKeyError()
    data class StorageFailure(val cause: Throwable) : RootKeyError()

    // --- F-5 additions (T608, D2) ---
    /** Argon2id unwrap провалился — пользователь ввёл неправильный passphrase. */
    object WrongPassphrase : RootKeyError() {
        override fun toString(): String = "RootKeyError.WrongPassphrase"
    }

    /**
     * Blob не поддаётся десериализации или содержит структурно невалидные данные.
     * @param cause Оригинальное exception если доступно.
     */
    data class CorruptedBlob(val cause: Throwable? = null) : RootKeyError()

    /**
     * `stableId` не назначен этой identity — `workers/identity/ POST /init-claim`
     * не был вызван или JWT claims ещё не обновлены.
     */
    object NoIdentity : RootKeyError() {
        override fun toString(): String = "RootKeyError.NoIdentity"
    }
}
