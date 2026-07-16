package com.launcher.preset.model

import kotlinx.serialization.Serializable

@Serializable
data class Blueprint(
    val id: String,
    val component: Component,
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
    val descriptionKey: String? = null,
    // T017 (FR-006, FR-014): v2 additions. Defaults keep v1 pool.json deserializing.
    /** IDs that must appear earlier in `Preset.wizardFlow`. null = no dependencies. */
    val requires: List<String>? = null,
    /** Wizard complete only when all `required=true` declarations reach Applied. */
    val required: Boolean = false,
)

@Serializable
data class Pool(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val declarations: List<Blueprint>,
) {
    fun byId(id: String): Blueprint? =
        declarations.firstOrNull { it.id == id }

    companion object {
        /** v2: adds `requires` + `required` to Blueprint (TASK-126). */
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
