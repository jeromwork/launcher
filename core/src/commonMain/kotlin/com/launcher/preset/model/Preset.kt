package com.launcher.preset.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
