package family.keys.api

/**
 * Ошибки [RootKeyManager] операций (FR-025).
 *
 *  • [KeystoreInvalidated] — hardware-backed key инвалидирован OS update / biometric
 *    enrollment change. Trigger recovery flow.
 *  • [RecoveryRequired] — Keystore пуст для этой identity; нужен passphrase recovery.
 *  • [StorageFailure] — IO error при чтении/записи wrapped root key.
 */
sealed class RootKeyError {
    object KeystoreInvalidated : RootKeyError()
    object RecoveryRequired : RootKeyError()
    data class StorageFailure(val cause: Throwable) : RootKeyError()
}
