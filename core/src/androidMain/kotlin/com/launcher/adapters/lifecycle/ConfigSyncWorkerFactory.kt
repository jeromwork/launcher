package com.launcher.adapters.lifecycle

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.launcher.adapters.paired.UnlinkCleanupWorker
import com.launcher.api.config.ConfigApplier
import com.launcher.api.link.LinkRegistry
import com.launcher.api.paired.LocalLinkRevocationStore

/**
 * WorkerFactory that resolves the constructor parameters of every DI-injected
 * CoroutineWorker in the project (spec 008 + spec 010).
 *
 * Workers registered here:
 *  - [ConfigRefreshWorker] — спек 008 FR-022 T3 (periodic config refresh).
 *  - [UnlinkCleanupWorker] — спек 010 T085-T087 (FR-032a server-side
 *    revocation cleanup).
 *
 * **Why custom factory needed**: both workers have non-default constructors
 * with Koin-injected dependencies. WorkManager's default factory only knows
 * the `(Context, WorkerParameters)` signature and would fail with a
 * reflection error.
 *
 * Wired in `LauncherApplication` via `Configuration.Builder().setWorkerFactory(...)`
 * — Application must implement `androidx.work.Configuration.Provider` for
 * WorkManager to pick up the override.
 *
 * If the worker class can't be matched (other workers from other features),
 * return null — WorkManager falls back to its default factory.
 *
 * TODO(refactor): когда the list of injected workers grows past 3-4, switch
 * to a `Map<String, (Context, WorkerParameters) -> ListenableWorker>` lookup
 * built from a Koin multi-binding so adding a worker is a one-line change.
 */
class ConfigSyncWorkerFactory(
    private val linkRegistry: LinkRegistry,
    private val configApplier: ConfigApplier,
    private val revocationStore: LocalLinkRevocationStore,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            ConfigRefreshWorker::class.java.name -> ConfigRefreshWorker(
                context = appContext,
                workerParams = workerParameters,
                linkRegistry = linkRegistry,
                configApplier = configApplier,
            )
            UnlinkCleanupWorker::class.java.name -> UnlinkCleanupWorker(
                context = appContext,
                workerParams = workerParameters,
                linkRegistry = linkRegistry,
                revocationStore = revocationStore,
            )
            else -> null
        }
    }
}
