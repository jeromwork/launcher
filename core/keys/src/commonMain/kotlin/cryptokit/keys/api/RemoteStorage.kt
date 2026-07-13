package cryptokit.keys.api

/**
 * **Public API of the keys / crypto layer** for storing opaque application bytes
 * in a remote namespace with end-to-end encryption applied transparently.
 *
 * Caller-side code (app modules, future ecosystem apps — messenger, album)
 * interacts only with this port:
 *
 * ```
 * remoteStorage.put(namespace = uid, key = "config/default", bytes = jsonBytes)
 * val bytes = remoteStorage.get(namespace = uid, key = "config/default")
 * ```
 *
 * **What the caller does NOT see and never has to think about**:
 *  - X25519 keypair generation, [DeviceId] allocation, public-key publication.
 *  - CEK generation, sealed-box wrapping, per-recipient blobs ([Envelope]).
 *  - Recipient resolution (own devices + delegated helpers).
 *  - Backend choice (Firestore today, own server later).
 *  - Wire-format schema versioning.
 *
 * **What the caller MUST know**:
 *  - `namespace` is the **owner UID** of the data, not the caller's UID. Editing
 *    another user's config (via access-grant) passes `namespace = ownerUid` and
 *    the implementation routes the write into the owner's storage, encrypted
 *    under the owner's resolved recipient list (which includes the caller because
 *    the owner gave them a grant).
 *  - `key` is the logical path inside the namespace (`config/default`,
 *    `config/grannys-phone`, `photo/album/xxx.jpg`, etc.). Implementation maps
 *    `key` onto its backend storage layout.
 *
 * Errors are surfaced through [Outcome.Failure] with [StorageError] — caller never
 * sees [CipherError] or any other crypto-internal sealed types.
 *
 * ---
 *
 * **TODO(server-blind-keys):** at present the `key` argument is used verbatim in the
 * backend path (Firestore: `/users/{namespace}/data/{escapedKey}`). This leaks logical
 * intent to the server operator — e.g. the string `config/default` telegraphs "this is
 * a configuration document". Future evolution: derive a deterministic opaque key on the
 * caller side via HKDF(rootKey, logicalName) → base64 before passing it here, so the
 * on-wire key is a pseudo-random string. This is **not a one-way door** — the port
 * signature stays as `key: String`, the change is purely inside callers; already-stored
 * documents remain readable by the same call site that wrote them. Not scheduled;
 * relevant when tightening server-side threat model. Discovered during TASK-66 closure
 * (see specs/task-66-generic-encrypted-bucket-registry/spec.md).
 */
interface RemoteStorage {

    /**
     * Encrypt [bytes] under the recipient list resolved for `(namespace, key)`
     * and persist into the backend. Idempotent overwrite of any existing value at
     * the same path.
     *
     * @param namespace Owner UID of the data — the user whose namespace holds
     *   the entry. Caller's own UID for self-edit; another user's UID when
     *   editing via access-grant.
     * @param key Logical path within `namespace`. Stable; reused across writes
     *   to update the same entry.
     * @param bytes Plaintext payload, up to [MAX_ENTRY_BYTES].
     */
    suspend fun put(namespace: String, key: String, bytes: ByteArray): Outcome<Unit, StorageError>

    /**
     * Read and decrypt the entry at `(namespace, key)` using this device's
     * private key. Returns the plaintext bytes.
     *
     * Fails with [StorageError.NotARecipient] if this device's [DeviceId] is not
     * a recipient in the stored envelope.
     */
    suspend fun get(namespace: String, key: String): Outcome<ByteArray, StorageError>

    /**
     * List logical keys in `namespace` matching the optional [keyPrefix].
     * Used by recovery flow ("restore all my configs") and admin UI ("show all
     * named configs of this owner").
     */
    suspend fun list(namespace: String, keyPrefix: String = ""): Outcome<List<String>, StorageError>

    /** Delete the entry at `(namespace, key)`. No-op if absent (returns Success). */
    suspend fun delete(namespace: String, key: String): Outcome<Unit, StorageError>

    companion object {
        /**
         * Maximum plaintext size for one entry. Aligned with [Envelope] backend
         * limits (Firestore document max 1 MiB minus envelope overhead).
         */
        const val MAX_ENTRY_BYTES: Int = 256 * 1024
    }
}
