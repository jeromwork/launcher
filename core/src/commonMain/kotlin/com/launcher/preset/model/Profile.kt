package com.launcher.preset.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProfileState(
    val opaque: JsonObject = JsonObject(emptyMap()),
)

/**
 * Entity — one row of the profile "table" (ECS ≈ database table, see
 * `docs/architecture/preset-model.md`): an id, its data ([component]), its
 * lifecycle state ([status]) and its place in the screen tree ([parentId]).
 */
@Serializable
data class Entity(
    val id: String,
    val component: Component,
    val wizardBehavior: WizardBehavior,
    val critical: Boolean,
    val status: ComponentStatus = ComponentStatus.Pending,
    /**
     * T127-009 (FR-011) — hierarchy **by reference**; `null` = root.
     *
     * Storage stays flat: the tree (Workspace → Flow → Tile, Toolbar →
     * ToolbarButton) is *computed* by queries, never nested in the wire format.
     * Same pattern as Bevy / Unity DOTS `Parent` and Android Launcher3's
     * `favorites.container` (research.md R-7).
     *
     * Defaulted, so profiles written before TASK-127 read back as flat roots.
     */
    val parentId: String? = null,
)

@Serializable
data class Profile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val basedOnPreset: String,
    val presetVersion: Int,
    /**
     * Legacy screen-wide grid. Superseded by `Component.Flow.layoutKey` — each
     * flow owns its grid (FR-013). Kept as the fallback for degenerate profiles
     * that declare no Workspace/Flow entities (US-1).
     *
     * TODO(layout-key-migration): remove once every bundled preset ships a
     * Workspace + Flow hierarchy.
     */
    val layoutKey: String,
    val components: List<Entity> = emptyList(),
    val preWizardSnapshot: Profile? = null,
    val snapshotTimestamp: Long? = null,
    val unknownRefs: List<String> = emptyList(),
    val state: ProfileState = ProfileState(),
) {
    fun mark(id: String, status: ComponentStatus): Profile =
        copy(components = components.map { if (it.id == id) it.copy(status = status) else it })

    fun replaceComponent(id: String, newComponent: Component): Profile =
        copy(components = components.map { if (it.id == id) it.copy(component = newComponent) else it })

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
