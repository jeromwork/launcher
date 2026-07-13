package cryptokit.keys.api

/**
 * Ошибки recovery flow (FR-027).
 *
 *  • [WrongPassphrase] — AEAD auth tag mismatch при unwrap'е. UI шанс попробовать
 *    ещё раз. Отличимо от [MalformedVault] (corruption).
 *  • [MalformedVault] — vault shape broken (corruption / unsupported algorithm).
 *  • [NoVaultPresent] — recovery невозможен (vault не существует в Firestore).
 *  • [TooManyAttempts] — 3 fail подряд → lockout (H-1 mitigation).
 *  • [Cancelled] — user отменил passphrase prompt.
 */
sealed class RecoveryError {
    object WrongPassphrase : RecoveryError()
    object MalformedVault : RecoveryError()
    object NoVaultPresent : RecoveryError()
    object TooManyAttempts : RecoveryError()
    object Cancelled : RecoveryError()
}
