package com.launcher.api.wizard

import com.launcher.api.wizard.data.StepEntry
import com.launcher.api.wizard.data.WizardManifest
import kotlinx.coroutines.flow.StateFlow

@Deprecated(
    "Superseded by TASK-120 ReconcileEngine — see com.launcher.preset.engine.ReconcileEngine. Removal scheduled for the draft-1 wizard refactor.",
)
interface WizardEngine {
    suspend fun run(manifest: WizardManifest): WizardOutcome
    fun currentState(): StateFlow<WizardState>

    /**
     * Mode flag (TASK-7 Phase 6). `WalkThrough` skips the
     * [computePending] pre-flight so the user sees every step with its
     * current value (Сценарий 5 in spec.md).
     */
    enum class Mode { Wizard, WalkThrough }

    /**
     * "Walk through all settings step-by-step" entry point (FR-014a /
     * Сценарий 5). Equivalent to [run] but never short-circuits, even
     * when every step's target state is already met — the user wants
     * to review and optionally re-confirm each one.
     *
     * UI hint for callers: render `Текущее: <value>. [Оставить] [Изменить]`
     * once per step. The actual Compose layer wiring lands when Settings
     * gels in Phase 6+; engine-level traversal is correct already.
     */
    suspend fun runWalkThrough(manifest: WizardManifest): WizardOutcome

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
