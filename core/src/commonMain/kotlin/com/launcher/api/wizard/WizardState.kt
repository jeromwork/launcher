package com.launcher.api.wizard

import kotlinx.serialization.json.JsonElement

typealias StepId = String

sealed class WizardState {
    data object Idle : WizardState()
    data class Running(
        val currentStepIndex: Int,
        val totalSteps: Int,
        val currentStep: WizardStep,
        val answers: Map<StepId, JsonElement>,
    ) : WizardState()
    data class Completed(val outcome: WizardOutcome.Completed) : WizardState()
}
