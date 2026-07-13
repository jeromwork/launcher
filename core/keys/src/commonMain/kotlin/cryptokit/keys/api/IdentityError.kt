package cryptokit.keys.api

/**
 * Ошибки [IdentityProof] операций (FR-028).
 *
 * Per data-model.md §5 — `NotSignedIn` это не error, а valid Outcome: операции
 * вроде `currentIdentity()` возвращают `Outcome.Success(null)` если не signed-in.
 *
 * NetworkFailure покрывается [Failure] с IOException-like cause.
 */
sealed class IdentityError {
    /** Provider не поддерживается на этом устройстве (non-GMS, Huawei). */
    object NoSupportedProvider : IdentityError()

    /** User cancelled Sign-In flow. */
    object Cancelled : IdentityError()

    /** Прочая ошибка — network, OAuth refusal, etc. */
    data class Failure(val cause: Throwable) : IdentityError()
}
