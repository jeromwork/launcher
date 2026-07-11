package com.launcher.api.wizard

import kotlinx.serialization.json.JsonElement

typealias StepId = String

@Deprecated(
    "Superseded by TASK-120 Profile + ProfileComponent state — see com.launcher.preset.model.Profile. Removal scheduled for the draft-1 wizard refactor.",
)
sealed class WizardState {
    data object Idle : WizardState()
    data class Running(
        val currentStepIndex: Int,
        val totalSteps: Int,
        val currentStep: WizardStep,
        val answers: Map<StepId, JsonElement>,
        // TASK-7 Phase 6 — when WalkThrough, UI renders
        // "Оставить" / "Изменить" instead of "Далее" / "Пропустить"
        // (FR-014a / Сценарий 5).
        val mode: WizardEngine.Mode = WizardEngine.Mode.Wizard,
    ) : WizardState()
    data class Completed(val outcome: WizardOutcome.Completed) : WizardState()
}
