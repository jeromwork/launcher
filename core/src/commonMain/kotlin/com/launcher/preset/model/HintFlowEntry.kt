package com.launcher.preset.model

import kotlinx.serialization.Serializable

/**
 * T014 — HintFlowEntry (FR-007).
 *
 * UI-layer metadata joining a wizard step with a localized hint from `hint-pool.json`.
 * [ReconcileEngine] never processes hints — they are consumed at render time by the
 * `WizardScreen` composable via the [HintPoolSource] port.
 *
 * @property hintId matches `HintDescriptor.hintId` in `hint-pool.json`; missing match
 *   → hint silently not rendered (hints are optional UX, not a correctness gate).
 * @property targetComponentId matches `Blueprint.id` in the pool — the wizard
 *   step next to which the hint appears.
 * @property textKey localization key used at render time.
 */
@Serializable
data class HintFlowEntry(
    val hintId: String,
    val targetComponentId: String,
    val textKey: String,
)
