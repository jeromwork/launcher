package com.launcher.app.data.envelope

import cryptokit.keys.api.AsyncConfigPushQueue
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.PushStatus
import cryptokit.keys.api.QueueError
import cryptokit.keys.api.RemoteStorage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * mockBackend / dev-build [AsyncConfigPushQueue] — drives push synchronously
 * through the supplied [storage] and reports terminal status immediately.
 *
 * Same behaviour as the test [cryptokit.keys.fakes.InMemoryAsyncConfigPushQueue]
 * fake, copied here so the production mockBackend variant does not depend on
 * commonTest source set.
 *
 * Not persistence-bound — data is lost on process death. Acceptable for the
 * variant scope (mockBackend is dev builds).
 */
class InMemoryAsyncConfigPushQueueImpl(
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
        val workId = "mock-work-${workIdCounter.incrementAndGet()}"
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
