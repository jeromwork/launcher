package com.launcher.api.wizard.data

import com.launcher.api.wizard.Criticality
import com.launcher.api.wizard.StepType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Deprecated(
    "Superseded by TASK-120 Preset (three-field split: wizardFlow / settingsMap / activeComponents) — see com.launcher.preset.model.Preset. Removal scheduled for the draft-1 wizard refactor.",
)
@Serializable
data class WizardManifestBody(
    val autoOrder: Boolean = false,
    val steps: List<StepEntry>? = null,
)

@Serializable
data class StepEntry(
    val stepType: WireStepType,
    val refId: String,
    val params: Map<String, JsonElement> = emptyMap(),
    val canSkip: Boolean = false,
    val criticality: WireCriticality? = null,
)

@Serializable
enum class WireStepType { UIChoice, SystemSetting, TutorialHint }

@Serializable
enum class WireCriticality { Required, Optional }

fun WireStepType.toDomain(): StepType = when (this) {
    WireStepType.UIChoice -> StepType.UIChoice
    WireStepType.SystemSetting -> StepType.SystemSetting
    WireStepType.TutorialHint -> StepType.TutorialHint
}

fun WireCriticality.toDomain(): Criticality = when (this) {
    WireCriticality.Required -> Criticality.Required
    WireCriticality.Optional -> Criticality.Optional
}

/** In-memory aggregate convenience type — header + body. */
data class WizardManifest(
    val header: ConfigDocumentHeader,
    val body: WizardManifestBody,
) {
    val id: String get() = header.id
    val schemaVersion: Int get() = header.schemaVersion
    val autoOrder: Boolean get() = body.autoOrder
    val steps: List<StepEntry>? get() = body.steps
}
