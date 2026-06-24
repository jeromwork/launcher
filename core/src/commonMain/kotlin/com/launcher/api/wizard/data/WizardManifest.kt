package com.launcher.api.wizard.data

import com.launcher.api.wizard.Criticality
import com.launcher.api.wizard.StepType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WizardManifestBody(
    val appFamilyId: String,
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
enum class WireStepType { UIChoice, SystemSetting, TutorialHint, Custom }

@Serializable
enum class WireCriticality { Required, Optional }

fun WireStepType.toDomain(): StepType = when (this) {
    WireStepType.UIChoice -> StepType.UIChoice
    WireStepType.SystemSetting -> StepType.SystemSetting
    WireStepType.TutorialHint -> StepType.TutorialHint
    // For Custom we always return the same sentinel `Custom("dispatch")`
    // so the engine's `steps[stepType]` map lookup matches a single
    // registered [CustomStep]; the per-refId dispatch happens inside
    // CustomStep itself (data-model.md §5.1).
    WireStepType.Custom -> StepType.Custom(CUSTOM_DISPATCH_KEY)
}

/**
 * Constant key used to register `CustomStep` in the engine's step map.
 * Inside `CustomStep.execute(...)`, the per-refId handler dispatch
 * resolves which actual flow runs.
 */
const val CUSTOM_DISPATCH_KEY: String = "dispatch"

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
    val appFamilyId: String get() = body.appFamilyId
    val autoOrder: Boolean get() = body.autoOrder
    val steps: List<StepEntry>? get() = body.steps
}
