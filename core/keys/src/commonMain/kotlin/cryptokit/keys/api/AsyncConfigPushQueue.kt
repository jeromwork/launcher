package cryptokit.keys.api

/**
 * Local-first push queue: caller hands off (namespace, key, plaintext) and gets
 * an opaque work id; the queue persists the payload and pushes asynchronously
 * via the backend with retry-on-network-failure.
 *
 * **Why a queue port instead of synchronous [RemoteStorage.put]**: senior persona
 * cannot tolerate UI block on every save. Local DataStore confirms the save
 * instantly; the cloud push runs in the background and surfaces failure only
 * if all retries exhausted.
 *
 * **Retry policy**: 5 attempts max with exponential backoff (per spec 018 F-5b
 * Q5 answer 2026-06-20). Constraint: NetworkType.CONNECTED.
 *
 * **Persistence**: implementation MUST survive app kill within the retry window —
 * payload staged to disk before [enqueue] returns. The Android implementation
 * uses an app-private file under `cache/envelope-push/{workId}.payload` plus
 * WorkManager metadata.
 */
interface AsyncConfigPushQueue {

    /**
     * Stage [bytes] for asynchronous push to `(namespace, key)`. Returns
     * opaque `workId` that the caller may surface in UI ("sync pending"
     * indicator) or use to query [status].
     *
     * Idempotent if called multiple times with identical `(namespace, key)` —
     * implementation cancels prior pending work for the same key and replaces
     * with the new payload (last-write-wins; UI shows the latest version).
     */
    suspend fun enqueue(
        namespace: String,
        key: String,
        bytes: ByteArray
    ): Outcome<String, QueueError>

    /** Inspect current state of a previously [enqueue]'d push. */
    suspend fun status(workId: String): PushStatus

    /** Cancel a pending push by [workId]. No-op if already running or done. */
    suspend fun cancel(workId: String): Outcome<Unit, QueueError>
}

enum class PushStatus {
    /** Pending — waiting for network / WorkManager scheduling. */
    Pending,
    /** Running — currently executing the push. */
    Running,
    /** Succeeded — payload stored remotely; staging cleaned up. */
    Succeeded,
    /** Failed — all retries exhausted; staging cleaned up. Caller may re-enqueue. */
    Failed,
    /** Cancelled — by [cancel] or replaced by a newer enqueue for the same key. */
    Cancelled,
    /** Unknown — workId not recognised by this queue. */
    Unknown
}

sealed class QueueError {
    data object TooLarge : QueueError()
    data class StagingFailure(val message: String) : QueueError()
    data class SchedulingFailure(val message: String) : QueueError()
}
