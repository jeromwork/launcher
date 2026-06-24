package com.launcher.ui.wizard.steps

import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridge between the WizardEngine (which awaits `step.execute(params)`) and
 * the Compose UI layer (which calls `submitAnswer` / `requestSkip` / etc.).
 *
 * The engine calls `execute()` on the step → step constructs a
 * [PendingExecution] → UI binds to its [params] for rendering → user action
 * resolves [completion] → engine resumes.
 *
 * Modelled as a shared queue so the same step instance can serve multiple
 * sequential invocations in a wizard run.
 */
class StepHost {
    @Volatile
    private var current: PendingExecution? = null

    val pending: PendingExecution? get() = current

    suspend fun await(params: StepParams): StepResult {
        val deferred = CompletableDeferred<StepResult>()
        current = PendingExecution(params, deferred)
        return try {
            deferred.await()
        } finally {
            current = null
        }
    }

    fun resolve(result: StepResult): Boolean {
        val pending = current ?: return false
        return pending.completion.complete(result)
    }
}

data class PendingExecution(
    val params: StepParams,
    val completion: CompletableDeferred<StepResult>,
)
