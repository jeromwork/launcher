package family.keys.api

/**
 * Ошибки [KeyRegistry] операций (FR-025).
 *
 * Per contracts/key-registry-v1.md:
 *  • [NotFound] — DEK с таким именем не зарегистрирован.
 *  • [UnknownDek] — DEK существует в storage, но с unknown algorithm/version
 *    (forward-compat scenario, FR-005).
 *  • [RootKeyUnavailable] — нельзя unwrap DEK потому что RootKey ещё не загружен
 *    (Keystore lockout, recovery required).
 *  • [StorageFailure] — IO/persistence error.
 */
sealed class KeyRegistryError {
    object NotFound : KeyRegistryError()
    object UnknownDek : KeyRegistryError()
    object RootKeyUnavailable : KeyRegistryError()
    data class StorageFailure(val cause: Throwable) : KeyRegistryError()
}
