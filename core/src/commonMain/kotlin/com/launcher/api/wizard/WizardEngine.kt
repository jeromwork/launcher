package com.launcher.api.wizard

import com.launcher.api.wizard.data.StepEntry
import com.launcher.api.wizard.data.WizardManifest
import kotlinx.coroutines.flow.StateFlow

interface WizardEngine {
    suspend fun run(manifest: WizardManifest): WizardOutcome
    fun currentState(): StateFlow<WizardState>

    /**
     * State-of-device pre-flight: returns the manifest's [StepEntry] list
     * filtered to entries whose target state is **not yet applied** on this
     * device. Per data-model.md §2.1 + plan.md Phase 1 (config-check master
     * pattern, Article VII §14).
     *
     * Dispatch by step type:
     *  - `SystemSetting` → keep when `SystemSettingPort.status(refId) != Applied`
     *    (Indeterminate is kept — graceful degradation).
     *  - `UIChoice` → keep when `UserPreferences.hasValueFor(refId)` is false.
     *  - `TutorialHint` → keep when `DismissedHintsStore.isDismissed(refId)` is false.
     *  - `Custom` → always keep (handler decides at execute time).
     */
    suspend fun computePending(manifest: WizardManifest): List<StepEntry>

    /**
     * Legacy diff: replaced by [computePending] (Article VII §14). Retained
     * for backward compatibility; remove once all callers migrate.
     */
    @Deprecated(
        "Replaced by computePending() — see Article VII §14 / data-model §2.1",
        ReplaceWith("computePending(currentManifest)"),
    )
    suspend fun diffPending(
        savedCompletedManifest: WizardManifest?,
        currentManifest: WizardManifest,
    ): List<PendingStep>
}
