package com.launcher.api.result

/**
 * Typed result with explicit error channel. Used by ports introduced in spec 007
 * (`RemoteSyncBackend`, `IdentityProvider`, `PushSender`, `LinkRegistry`) where
 * a single `Throwable` would lose the discriminated-error shape that the domain
 * needs to react against (Offline vs PermissionDenied vs TransactionConflict).
 *
 * Named `Outcome` to avoid clashing with stdlib `kotlin.Result<T>`, which is
 * single-typed (Throwable-only) and not designed for sealed-error hierarchies.
 *
 * Naming convention: `Outcome.Success(value)` / `Outcome.Failure(error)`.
 * Domain code pattern-matches on `Outcome` to surface typed errors to callers
 * without coupling them to platform exception types.
 */
sealed interface Outcome<out T, out E> {
    data class Success<out T>(val value: T) : Outcome<T, Nothing>
    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}
