package com.launcher.preset.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Preset — shareable JSON template describing WHICH components to use and WHEN.
 *
 * The three list fields below (`wizardFlow` / `settingsMap` / `activeComponents`)
 * express the **LIFECYCLE dimension** of the model: they describe *when* a
 * component appears (during first-run wizard, in the Settings screen, or
 * currently applied at runtime).
 *
 * This is orthogonal to the **SEMANTIC dimension** carried by `Component.tags`
 * which describes *what* a component is about (Presentation, Safety, Accessibility, …).
 *
 * Do NOT conflate the two — a component may appear in `wizardFlow` AND carry
 * tags `[Presentation, Communication]`; both facets are independently useful.
 *
 * See `docs/architecture/preset-model.md` § "Two orthogonal dimensions" for full
 * discussion, and TASK-127 Decision block for the tag+query extension of TASK-120.
 */
@Serializable
data class WizardFlowEntry(
    val poolRef: String,
    val order: Int,
    val wizardTitleKey: String,
    val wizardIntroKey: String? = null,
    val behavior: WizardBehavior,
    val paramsOverride: JsonObject? = null,
    val visibleIf: JsonElement? = null,
)

@Serializable
data class SettingsMapEntry(
    val poolRef: String,
    val categoryKey: String,
    val settingsIcon: String? = null,
    val sensitivity: Sensitivity = Sensitivity.Normal,
    val paramsOverride: JsonObject? = null,
)

@Serializable
data class ActiveComponentEntry(
    val poolRef: String,
    val paramsOverride: JsonObject? = null,
    val status: ComponentStatus = ComponentStatus.Pending,
    /**
     * T127-026 (FR-011/FR-013) — id of the parent entity in the assembled Profile
     * (`null` = root). This is how a preset expresses the screen tree:
     * `Workspace → Flow → Tile`, `Toolbar → ToolbarButton`.
     *
     * Additive optional field: presets written before TASK-127 omit it and produce
     * a flat, all-roots profile (the simple-launcher case, US-1).
     * Validated at assembly — see [ValidationError.DanglingParentRef].
     */
    val parentRef: String? = null,
)

@Serializable
data class Preset(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val presetId: String,
    val version: Int,
    val layoutKey: String,
    val wizardFlow: List<WizardFlowEntry> = emptyList(),
    val settingsMap: List<SettingsMapEntry> = emptyList(),
    val activeComponents: List<ActiveComponentEntry> = emptyList(),
    // T015 (FR-007, FR-003): v2 additions. Nullable defaults keep v1 fixtures deserializing.
    val hintFlow: List<HintFlowEntry>? = null,
    val wizardPresentation: WizardPresentation? = null,
) {
    companion object {
        /** v2: adds `hintFlow` + `wizardPresentation` (TASK-126). */
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
