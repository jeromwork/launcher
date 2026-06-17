package com.launcher.ui.wizard.steps

import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.StepType
import com.launcher.api.wizard.WizardStep
import com.launcher.ui.wizard.TutorialHintManager
import kotlinx.serialization.json.JsonPrimitive

/**
 * TutorialHintStep — shows a tutorial hint overlay, awaits user dismissal.
 * Skips immediately if hint was previously dismissed.
 *
 * Per FR-023, FR-024, FR-025, US-7.
 */
class TutorialHintStep(
    private val host: StepHost,
    private val hintManager: TutorialHintManager,
    override val canSkip: Boolean = true,
    override val canGoBack: Boolean = true,
) : WizardStep {
    override val stepType: StepType = StepType.TutorialHint

    override suspend fun execute(params: StepParams): StepResult {
        if (hintManager.isDismissed(params.refId)) {
            return StepResult.Skipped
        }
        val result = host.await(params)
        return when (result) {
            is StepResult.AnswerCaptured -> {
                hintManager.markDismissed(params.refId)
                StepResult.AnswerCaptured(JsonPrimitive("dismissed"))
            }
            else -> result
        }
    }
}
