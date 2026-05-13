package com.launcher.api.sync

/**
 * Discriminated error surface for [RemoteSyncBackend] operations (spec 007 §FR-013).
 *
 * Every error a Firestore SDK can throw at the adapter (or that a Fake adapter
 * can simulate) MUST be translated to one of these subtypes before crossing
 * the `androidMain/adapters → commonMain` boundary — see anti-corruption
 * requirement in CLAUDE.md §1, §2.
 *
 * Adding a new subtype is a wire-shape change for ports that surface it
 * outward; bump consumer call-sites in tandem.
 */
sealed interface BackendError {
    /** Network unreachable or adapter is in offline mode (see `FakeRemoteSyncBackend` queue/isStale per C5). */
    data object Offline : BackendError

    /** Authenticated but not authorised — Firestore Security Rules denied the operation. */
    data object PermissionDenied : BackendError

    /** Requested document does not exist. Reads of a missing doc return `Outcome.Success(null)`; this error
     *  is reserved for operations that REQUIRE existence (e.g. update without create). */
    data object NotFound : BackendError

    /** Firestore transaction aborted because a concurrent writer changed a read document. */
    data class TransactionConflict(val message: String) : BackendError

    /** Pairing-token-specific: read succeeded but the token has expired (now > expiresAt). */
    data object Expired : BackendError

    /** Catch-all for unmapped adapter errors. `message` is for logging only — do not surface to user. */
    data class Unknown(val message: String) : BackendError
}
