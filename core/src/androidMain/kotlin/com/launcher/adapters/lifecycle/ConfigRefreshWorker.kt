package com.launcher.adapters.lifecycle

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.launcher.api.config.ConfigApplier
import com.launcher.api.config.ConfigSyncConstants
import com.launcher.api.link.LinkRegistry
import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh trigger for `/config/current` (spec 008 Phase 7
 * T094, FR-022 T3).
 *
 * Per research.md §5: this is **fallback polling** — fires every 15 minutes
 * via WorkManager. Active triggers (FCM T1, NetworkCallback T2, RESUMED T4)
 * cover most cases; this catches gaps when:
 *  - device has no GMS (FCM unavailable, C13 inheritance from спека 007);
 *  - prolonged offline burst means accumulated FCM pushes dropped;
 *  - NetworkCallback misses transitions on some OEMs (Article VI §6 mitigation).
 *
 * Per Article IX §3: this IS a polling task, justified by C13 + redundancy.
 * 96 wakeups/day, <0.1% battery. Doze-aware via WorkManager's NetworkType
 * constraint (won't run без network).
 *
 * Per research.md §8: NO commonMain port abstraction — WorkManager is highly
 * Android-specific (Doze, system scheduler), faking provides no test value.
 * Inline in this androidMain file.
 *
 * Wiring (handled by Phase 8 DI setup):
 *  - On app start, call [schedulePeriodicRefresh] once;
 *  - Worker reads current link from [LinkRegistry], invokes [ConfigApplier].
 */
class ConfigRefreshWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val linkRegistry: LinkRegistry,
    private val configApplier: ConfigApplier,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val link = linkRegistry.currentLink().first()
            ?: return Result.success() // No active link — nothing to refresh.

        return when (configApplier.applyFromRemote(link.linkId)) {
            is Outcome.Success -> Result.success()
            is Outcome.Failure -> Result.retry() // Backoff; WorkManager handles exponential.
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "config-refresh-periodic"

        /**
         * Idempotent: call from Application init or after first link bind.
         * Re-invocations keep the existing schedule (per
         * [ExistingPeriodicWorkPolicy.KEEP]).
         */
        fun schedulePeriodicRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ConfigRefreshWorker>(
                ConfigSyncConstants.WORKMANAGER_POLL_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Cancel scheduling (e.g., on revoke). */
        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
