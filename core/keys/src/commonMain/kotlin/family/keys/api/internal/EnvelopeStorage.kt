package family.keys.api.internal

import family.keys.api.Envelope
import family.keys.api.Outcome

/**
 * Internal port: write/read of [Envelope] documents in the backend.
 *
 * One [Envelope] = one backend document. Atomic round-trips. Backend layout is
 * implementation-defined (Firestore native typed fields, own server JSON, etc.) —
 * domain code never depends on it.
 *
 * Layout in Firestore today:
 *   `/users/{namespace}/data/{escapedKey}`
 *   where `escapedKey` is `key` with `/` replaced by `__` to fit Firestore
 *   document-id constraints.
 */
interface EnvelopeStorage {

    /** Atomically store [envelope] at `(namespace, key)`, overwriting any prior. */
    suspend fun store(namespace: String, key: String, envelope: Envelope): Outcome<Unit, EnvelopeStorageError>

    /** Atomically load the envelope at `(namespace, key)`. */
    suspend fun load(namespace: String, key: String): Outcome<Envelope, EnvelopeStorageError>

    /** List logical keys present in [namespace] matching [keyPrefix]. */
    suspend fun list(namespace: String, keyPrefix: String): Outcome<List<String>, EnvelopeStorageError>

    /** Delete entry at `(namespace, key)`. No-op if absent. */
    suspend fun delete(namespace: String, key: String): Outcome<Unit, EnvelopeStorageError>
}

sealed class EnvelopeStorageError {
    data class Network(val cause: Throwable? = null) : EnvelopeStorageError()
    data object Unauthorized : EnvelopeStorageError()
    data object NotFound : EnvelopeStorageError()
    data class Malformed(val message: String) : EnvelopeStorageError()
}
