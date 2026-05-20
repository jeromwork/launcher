package com.launcher.adapters.paired

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.launcher.api.link.LinkRegistry
import com.launcher.api.paired.LocalLinkRevocationStore
import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Spec 010 T085-T087 — server-side cleanup queued by [LocalLinkRevocationStore]
 * (FR-032a). Implements **four paths** documented в FR-032a:
 *
 *  - **(a) Online**: enqueue happens с интернетом → Worker runs immediately →
 *    `LinkRegistry.revoke()` succeeds → store flag cleared. One round trip.
 *  - **(b) Offline**: WorkManager honours [Constraints.NetworkType.CONNECTED]
 *    constraint — Worker stays queued; locally Маша already disappeared (the
 *    store flag flipped before enqueue per [PairedDevicesPresenter]).
 *  - **(c) Reconnect**: WorkManager fires Worker on first network-up;
 *    [doWork] re-checks the local flag — если уже cleared (admin удалил
 *    параллельно через другой канал), no-op return success.
 *  - **(d) Retry on failure**: [Result.retry] triggers WorkManager's
 *    exponential backoff (BackoffPolicy.EXPONENTIAL) — каждый следующий
 *    attempt спустя удваивающийся interval.
 *
 * Idempotency guarantee (FR-032a (c)): when a second enqueue lands for the
 * same linkId, [ExistingWorkPolicy.KEEP] makes the second enqueue a no-op so
 * we don't fork the retry chain.
 *
 * **Decision: revoke() vs deactivate(linkId)**: the спек 007 [LinkRegistry]
 * port has only [revoke] (no params) — multi-admin is OUT-013 future-spec и
 * registry holds at-most-one link. So [doWork] checks that the queued linkId
 * matches the current registry link before calling [LinkRegistry.revoke]; if
 * it doesn't (e.g., user re-paired with another admin while the worker
 * waited), we treat the queued entry as obsolete and clear it.
 *
 * TODO(spec 008 multi-admin): when [LinkRegistry] grows
 * `deactivate(linkId)`, replace the current-link check + revoke() with a
 * direct call so simultaneous links can be revoked independently.
 */
class UnlinkCleanupWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val linkRegistry: LinkRegistry,
    private val revocationStore: LocalLinkRevocationStore,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val linkId = inputData.getString(KEY_LINK_ID) ?: return Result.success()

        // FR-032a (c) idempotency: store flag may already be cleared by an
        // earlier successful run or by a parallel admin-side delete.
        if (!revocationStore.isRevoked(linkId).first()) {
            return Result.success()
        }

        val current = linkRegistry.currentLink().first()
        if (current == null || current.linkId != linkId) {
            // The queued link is no longer the active registry entry — treat
            // as obsolete and clear so we don't retry forever.
            revocationStore.clearRevoked(linkId)
            return Result.success()
        }

        return when (linkRegistry.revoke()) {
            is Outcome.Success -> {
                revocationStore.clearRevoked(linkId)
                Result.success()
            }
            is Outcome.Failure -> Result.retry()
        }
    }

    companion object {
        const val KEY_LINK_ID: String = "linkId"
        const val UNIQUE_WORK_PREFIX: String = "unlink_"

        /**
         * FR-032a (b)/(c): enqueue с [NetworkType.CONNECTED] constraint so
         * WorkManager defers execution until network is available.
         * [ExistingWorkPolicy.KEEP] makes the call idempotent — re-confirming
         * unlink before the worker ran is a no-op.
         */
        fun enqueue(context: Context, linkId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<UnlinkCleanupWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_LINK_ID to linkId))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_WORK_PREFIX$linkId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

