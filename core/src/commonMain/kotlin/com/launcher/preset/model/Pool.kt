package com.launcher.preset.model

import kotlinx.serialization.Serializable

/**
 * Blueprint — a **Bundle** (canonical ECS, TASK-136): a named component set +
 * tags used as a **spawn-time template only**. Verified against Bevy `Bundle`:
 * "zero runtime significance after creation" — after `ProfileFactory` spawns an
 * [Entity] from it, the bundle identity is discarded; the entity is a free bag
 * (add/remove components independently).
 */
@Serializable
data class Blueprint(
    val id: String,
    val components: List<Component> = emptyList(),
    /** Tags the bundle stamps onto the spawned entity (CL-4 — explicit). */
    val tags: Set<Tag> = emptySet(),
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
