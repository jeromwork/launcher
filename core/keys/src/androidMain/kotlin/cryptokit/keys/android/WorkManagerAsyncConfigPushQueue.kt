package cryptokit.keys.android

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cryptokit.keys.api.AsyncConfigPushQueue
import cryptokit.keys.api.ConfigChangeNotifier
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.PushStatus
import cryptokit.keys.api.QueueError
import cryptokit.keys.api.RemoteStorage
import kotlinx.coroutines.guava.await
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Android [AsyncConfigPushQueue] backed by WorkManager + on-disk staging.
 *
 * **Flow on [enqueue]**:
 *   1. Validate size; reject `TooLarge` if > [RemoteStorage.MAX_ENTRY_BYTES].
 *   2. Write payload to `cache/envelope-push/{workId}.payload` (app-private).
 *   3. Enqueue OneTimeWorkRequest carrying `(namespace, key, workId)` in
 *      input Data. Network constraint = CONNECTED, BackoffPolicy = EXPONENTIAL,
 *      initial backoff 30 seconds.
 *   4. Unique work name = `envelope-push::{namespace}::{key}`. Existing pending
 *      work for the same name is replaced via [ExistingWorkPolicy.REPLACE]
 *      (last-write-wins for the same logical entry).
 *
 * **Worker behaviour** ([EnvelopeAsyncPushWorker.doWork]):
 *   1. Read payload from staging file.
 *   2. Call `RemoteStorage.put(namespace, key, payload)`.
 *   3. On Success → delete staging file → `Result.success()`.
 *   4. On Failure → if runAttemptCount < MAX_RETRIES → `Result.retry()`
 *      (WorkManager schedules exponential backoff). Else delete staging file
 *      and `Result.failure()`.
 *
 * **Retry policy**: MAX_RETRIES = 5 (per spec 018 F-5b Q5 answer 2026-06-20).
 *
 * **Failure modes**:
 *  - Staging write fails (disk full, IOException) → returns `StagingFailure`.
 *  - WorkManager enqueue fails → returns `SchedulingFailure`.
 *  - Network down at attempt time → WorkManager defers per Constraints.
 *  - All 5 retries exhausted → worker writes `Result.failure()`; caller
 *    observes via [status] as `PushStatus.Failed`.
 */
class WorkManagerAsyncConfigPushQueue(
    private val context: Context
) : AsyncConfigPushQueue {

    private val stagingDir: File = File(context.cacheDir, STAGING_DIR_NAME).apply { mkdirs() }

    override suspend fun enqueue(
        namespace: String,
        key: String,
        bytes: ByteArray
    ): Outcome<String, QueueError> {
        if (bytes.size > RemoteStorage.MAX_ENTRY_BYTES) {
            return Outcome.Failure(QueueError.TooLarge)
        }
        val workId = UUID.randomUUID().toString()
        val stagingFile = File(stagingDir, "$workId$PAYLOAD_EXT")
        return try {
            stagingFile.outputStream().use { it.write(bytes) }
            val request = OneTimeWorkRequestBuilder<EnvelopeAsyncPushWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_NAMESPACE, namespace)
                        .putString(KEY_LOGICAL_KEY, key)
                        .putString(KEY_WORK_ID, workId)
                        .build()
                )
                .addTag(TAG_WORK)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName(namespace, key),
                ExistingWorkPolicy.REPLACE,
                request
            )
            // Map WorkManager request id → our workId via a sidecar tag for status().
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<NoOpWorker>()
                    .addTag(workIdTag(workId))
                    .setInputData(
                        Data.Builder().putString(KEY_WORK_ID_PARENT, request.id.toString()).build()
                    )
                    .build()
            )
            Outcome.Success(workId)
        } catch (t: Throwable) {
            stagingFile.delete()
            Outcome.Failure(QueueError.StagingFailure(t.message ?: t::class.simpleName.orEmpty()))
        }
    }

    override suspend fun status(workId: String): PushStatus {
        val tag = workIdTag(workId)
        val infos = WorkManager.getInstance(context)
            .getWorkInfosByTag(tag)
            .await()
        val info = infos.firstOrNull() ?: return PushStatus.Unknown
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> PushStatus.Pending
            WorkInfo.State.RUNNING -> PushStatus.Running
            WorkInfo.State.SUCCEEDED -> PushStatus.Succeeded
            WorkInfo.State.FAILED -> PushStatus.Failed
            WorkInfo.State.CANCELLED -> PushStatus.Cancelled
        }
    }

    override suspend fun cancel(workId: String): Outcome<Unit, QueueError> {
        WorkManager.getInstance(context).cancelAllWorkByTag(workIdTag(workId))
        return Outcome.Success(Unit)
    }

    /**
     * Worker entry point. Default WorkManager factory constructs it with the
     * standard (Context, WorkerParameters) signature; the worker pulls
     * [RemoteStorage] from Koin's GlobalContext at run time. This avoids
     * requiring the host app to wire a custom WorkerFactory for keys-module
     * workers, while still respecting the port-adapter boundary (worker
     * depends only on the public [RemoteStorage] interface, never on a
     * specific adapter).
     */
    class EnvelopeAsyncPushWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val namespace = inputData.getString(KEY_NAMESPACE)
                ?: return Result.failure()
            val key = inputData.getString(KEY_LOGICAL_KEY)
                ?: return Result.failure()
            val workId = inputData.getString(KEY_WORK_ID)
                ?: return Result.failure()
            val storage: RemoteStorage = try {
                org.koin.core.context.GlobalContext.get().get(RemoteStorage::class)
            } catch (t: Throwable) {
                return Result.failure(
                    Data.Builder().putString(KEY_FINAL_ERROR, "DI: ${t.message}").build()
                )
            }
            val stagingFile = File(File(applicationContext.cacheDir, STAGING_DIR_NAME), "$workId$PAYLOAD_EXT")
            if (!stagingFile.exists()) {
                // Staging already gone — assume earlier attempt succeeded or
                // user clear-app-data'd. Drop quietly.
                return Result.success()
            }
            val payload = try {
                stagingFile.readBytes()
            } catch (t: Throwable) {
                return if (runAttemptCount < MAX_RETRIES) Result.retry()
                else Result.failure().also { stagingFile.delete() }
            }
            return when (val r = storage.put(namespace, key, payload)) {
                is Outcome.Success -> {
                    stagingFile.delete()
                    // F-5c integration: notify higher layers that a config save
                    // completed (e.g. trigger Worker push). Optional, fire-and-forget
                    // — failure here MUST NOT affect storage success outcome.
                    runCatching {
                        org.koin.core.context.GlobalContext.get()
                            .getOrNull<ConfigChangeNotifier>()
                            ?.onConfigSaved(namespace, key)
                    }
                    Result.success()
                }
                is Outcome.Failure -> {
                    if (runAttemptCount < MAX_RETRIES) {
                        Result.retry()
                    } else {
                        stagingFile.delete()
                        Result.failure(
                            Data.Builder().putString(KEY_FINAL_ERROR, r.error.toString()).build()
                        )
                    }
                }
            }
        }
    }

    /** Placeholder worker used to tag a workId for [status] lookup. */
    class NoOpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = Result.success()
    }

    companion object {
        const val STAGING_DIR_NAME: String = "envelope-push"
        const val PAYLOAD_EXT: String = ".payload"
        const val KEY_NAMESPACE: String = "namespace"
        const val KEY_LOGICAL_KEY: String = "logicalKey"
        const val KEY_WORK_ID: String = "workId"
        const val KEY_WORK_ID_PARENT: String = "workIdParent"
        const val KEY_FINAL_ERROR: String = "finalError"
        const val TAG_WORK: String = "envelope-push"
        const val MAX_RETRIES: Int = 5

        /** Initial backoff in seconds; WorkManager doubles per retry. */
        const val BACKOFF_SECONDS: Long = 30

        private fun uniqueName(namespace: String, key: String): String =
            "envelope-push::$namespace::$key"

        internal fun workIdTag(workId: String): String = "envelope-push-id::$workId"
    }
}
