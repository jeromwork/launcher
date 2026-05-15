package com.launcher.adapters.lifecycle

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.launcher.api.config.ConfigApplier
import com.launcher.api.link.LinkRegistry

/**
 * WorkerFactory that resolves [ConfigRefreshWorker]'s constructor parameters
 * from Koin DI (spec 008 Phase 7 / DI-wiring task).
 *
 * **Why custom factory needed**: [ConfigRefreshWorker] has non-default
 * constructor с [LinkRegistry] + [ConfigApplier] dependencies. WorkManager's
 * default factory only knows the `(Context, WorkerParameters)` signature and
 * would fail с reflection error.
 *
 * Wired in `LauncherApplication` via `Configuration.Builder().setWorkerFactory(...)`
 * — Application must implement `androidx.work.Configuration.Provider` for
 * WorkManager to pick up the override.
 *
 * If the worker class can't be matched (other workers from other features),
 * return null — WorkManager falls back to its default factory.
 */
class ConfigSyncWorkerFactory(
    private val linkRegistry: LinkRegistry,
    private val configApplier: ConfigApplier,
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
            else -> null
        }
    }
}
