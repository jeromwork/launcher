package com.launcher.app.boot

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.launcher.app.preset.task120.PresetBootstrap
import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.model.RunMode
import org.koin.core.context.GlobalContext

/**
 * TASK-126 Phase 4 T081 (FR-012, US-4, CL-9) — actual boot-check reconcile.
 *
 * 1. `PresetBootstrap.bootstrap()` — no-op if a Profile already exists;
 *    otherwise loads the bundled preset (defensive — normally the profile
 *    already exists after first launch).
 * 2. `ReconcileEngine.run(RunMode.BootCheck)` — walks `critical=true`
 *    components only, dispatches `Provider.check()` / `apply()`.
 * 3. Profile is persisted incrementally by the engine itself; we do not
 *    need an explicit save here.
 *
 * Dependencies are pulled from Koin `GlobalContext` rather than via
 * `ConfigSyncWorkerFactory` — the worker has no per-instance state that
 * would benefit from constructor injection, and this keeps the factory
 * change surface small (a future refactor to a multi-binding factory is
 * flagged in `ConfigSyncWorkerFactory` docstring).
 */
class BootCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val koin = GlobalContext.get()
        val bootstrap = koin.get<PresetBootstrap>()
        val engine = koin.get<ReconcileEngine>()

        val bootOutcome = bootstrap.bootstrap()
        Log.i(TAG, "PresetBootstrap outcome: $bootOutcome")

        engine.run(mode = RunMode.BootCheck)
        Log.i(TAG, "BootCheck reconcile complete")
        Result.success()
    } catch (t: Throwable) {
        Log.e(TAG, "BootCheck failed", t)
        Result.retry()
    }

    companion object {
        private const val TAG = "BootCheckWorker"
    }
}
