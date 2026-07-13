package cryptokit.keys.fakes

import cryptokit.keys.api.AsyncConfigPushQueue
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.PushStatus
import cryptokit.keys.api.QueueError
import cryptokit.keys.api.RemoteStorage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [AsyncConfigPushQueue] for tests.
 *
 * Drives push synchronously through the supplied [storage] — caller can
 * await `enqueue` completion to observe persisted state. Status transitions
 * (Pending → Running → Succeeded / Failed) are recorded so tests can verify
 * the queue contract without WorkManager.
 *
 * NOT thread-safe in an interesting way; it's a fake. Production
 * [AsyncConfigPushQueue] is [cryptokit.keys.android.WorkManagerAsyncConfigPushQueue].
 */
class InMemoryAsyncConfigPushQueue(
    private val storage: RemoteStorage
) : AsyncConfigPushQueue {

    private val statuses = ConcurrentHashMap<String, PushStatus>()
    private val workIdCounter = AtomicLong()

    override suspend fun enqueue(
        namespace: String,
        key: String,
        bytes: ByteArray
    ): Outcome<String, QueueError> {
        if (bytes.size > RemoteStorage.MAX_ENTRY_BYTES) {
            return Outcome.Failure(QueueError.TooLarge)
        }
        val workId = "fake-work-${workIdCounter.incrementAndGet()}"
        statuses[workId] = PushStatus.Running
        return when (val r = storage.put(namespace, key, bytes)) {
            is Outcome.Success -> {
                statuses[workId] = PushStatus.Succeeded
                Outcome.Success(workId)
            }
            is Outcome.Failure -> {
                statuses[workId] = PushStatus.Failed
                Outcome.Failure(QueueError.SchedulingFailure(r.error.toString()))
            }
        }
    }

    override suspend fun status(workId: String): PushStatus =
        statuses[workId] ?: PushStatus.Unknown

    override suspend fun cancel(workId: String): Outcome<Unit, QueueError> {
        statuses[workId] = PushStatus.Cancelled
        return Outcome.Success(Unit)
    }
}
