package family.keys.api

/**
 * Errors surfaced by [RemoteStorage]. Caller-facing — never leaks internal
 * crypto sealed types ([CipherError]) or backend-specific errors (Firestore SDK
 * exceptions). Implementation translates internal errors into one of these cases.
 */
sealed class StorageError {

    /** Caller is not signed in / no identity available — encryption cannot proceed. */
    data object NoIdentity : StorageError()

    /** Network failure during backend round-trip. Retry is appropriate. */
    data class Network(val cause: Throwable? = null) : StorageError()

    /** Backend rejected access — owner has not granted permission, or grant revoked. */
    data object Unauthorized : StorageError()

    /** The requested entry does not exist. */
    data object NotFound : StorageError()

    /** Plaintext exceeded [RemoteStorage.MAX_ENTRY_BYTES]. */
    data object TooLarge : StorageError()

    /**
     * This device is not in the envelope's recipient list. Either the owner has
     * not re-encrypted the entry to include this device yet, or grant was
     * revoked between write and read.
     */
    data object NotARecipient : StorageError()

    /**
     * Stored envelope failed integrity check (tampered, wrong key, mismatched
     * AAD). Same surface for all three causes — exposing detail would leak
     * crypto state to caller.
     */
    data object IntegrityFailure : StorageError()

    /** Stored envelope uses a schemaVersion or algorithm this build does not understand. */
    data object UnsupportedFormat : StorageError()

    /** Catch-all for unexpected backend / serialization issues. */
    data class Malformed(val message: String) : StorageError()
}
