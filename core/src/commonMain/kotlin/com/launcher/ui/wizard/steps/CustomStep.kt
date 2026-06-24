package com.launcher.ui.wizard.steps

import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.StepType
import com.launcher.api.wizard.WizardStep
import com.launcher.api.wizard.data.CUSTOM_DISPATCH_KEY

/**
 * Wizard step that dispatches `StepType.Custom` entries by `refId` to a
 * map of [CustomStepHandler]s. The engine registers exactly one
 * `CustomStep` against `StepType.Custom(CUSTOM_DISPATCH_KEY)`; this
 * step then routes each manifest entry to the right handler.
 *
 * Unknown refIds → [StepResult.Skipped] (graceful — see Article VII §15).
 *
 * Per data-model.md §5.1 + plan.md Phase 5.
 */
class CustomStep(
    private val handlers: Map<String, CustomStepHandler>,
) : WizardStep {
    override val stepType: StepType = StepType.Custom(CUSTOM_DISPATCH_KEY)
    override val canSkip: Boolean = true
    override val canGoBack: Boolean = true

    override suspend fun execute(params: StepParams): StepResult {
        val handler = handlers[params.refId] ?: return StepResult.Skipped
        return handler.execute(params)
    }
}

/**
 * Per-refId handler for a `StepType.Custom` step (e.g. `pair-admin`,
 * future `tutorial-replay`, etc.). Lives in a platform adapter module
 * when the implementation needs Android types.
 */
interface CustomStepHandler {
    suspend fun execute(params: StepParams): StepResult
}
