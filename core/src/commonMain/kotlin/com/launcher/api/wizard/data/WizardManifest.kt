package com.launcher.api.wizard.data

import com.launcher.api.wizard.Criticality
import com.launcher.api.wizard.StepType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WizardManifestBody(
    /**
     * Legacy field removed in TASK-65 schemaVersion=2 (FR-002). Kept nullable
     * so that any pre-migration body deserialized through fixtures still parses.
     * New code derives identity from [com.launcher.api.preset.PresetRef].
     */
    val appFamilyId: String? = null,
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
    val appFamilyId: String? get() = body.appFamilyId
    val autoOrder: Boolean get() = body.autoOrder
    val steps: List<StepEntry>? get() = body.steps
}
