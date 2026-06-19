package family.keys.api

/**
 * Ошибки [RecoveryKeyVault] операций (FR-025).
 *
 * Per contracts/recovery-vault-v1.md:
 *  • [NotFound] — vault для этого UID не существует (новый user).
 *  • [Unauthorized] — Firestore security rules denied (auth.uid != uid).
 *  • [Conflict] — другой client успел записать новый blob (transactional retry).
 *  • [Malformed] — blob shape не соответствует schema (corruption или unsupported version).
 *  • [SchemaDowngradeDetected] — fetched.schemaVersion < last seen (TOLU mitigation, H-2).
 *  • [Network] — IO error.
 */
sealed class VaultError {
    object NotFound : VaultError()
    object Unauthorized : VaultError()
    object Conflict : VaultError()
    object Malformed : VaultError()
    object SchemaDowngradeDetected : VaultError()
    data class Network(val cause: Throwable) : VaultError()
}
