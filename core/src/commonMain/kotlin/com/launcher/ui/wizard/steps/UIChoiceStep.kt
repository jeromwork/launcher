package com.launcher.ui.wizard.steps

import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.StepType
import com.launcher.api.wizard.WizardStep

/**
 * UIChoiceStep — handles language / theme / fontScale / grid /
 * screenLayout / tileSet choices. Reads from ui-customization.pool
 * (UIOptionEntry by refId), shows simple-choice or pick-from-bundled UI,
 * captures user answer.
 *
 * Per FR-008. Compose UI host (WizardHostScreen) binds to this step's
 * StepHost.
 */
class UIChoiceStep(
    private val host: StepHost,
    override val canSkip: Boolean = true,
    override val canGoBack: Boolean = true,
) : WizardStep {
    override val stepType: StepType = StepType.UIChoice
    override suspend fun execute(params: StepParams): StepResult = host.await(params)
}
