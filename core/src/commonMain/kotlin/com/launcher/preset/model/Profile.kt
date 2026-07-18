package com.launcher.preset.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

@Serializable
data class ProfileState(
    val opaque: JsonObject = JsonObject(emptyMap()),
)

/**
 * Entity — one row of the profile "World" (canonical ECS, TASK-136): an id, a
 * **free bag** of [components], a set of zero-data [tags], and its place in the
 * screen tree ([parentId]).
 *
 * No `status` field — apply-state is itself a [LifecycleState] component in the
 * bag (CL-5). No single `component` — an entity is *simply a set of components*
 * (ECS FAQ); components and tags are added/removed independently.
 *
 * **Invariant** (CL-3, fitness-enforced): at most one component per Kotlin type
 * in [components] ⇒ `entity.get<T>()` (see `preset/ecs/EntityDsl.kt`) is
 * unambiguous. [LifecycleState] counts as one slot regardless of variant.
 */
@Serializable
data class Entity(
    val id: String,
    val components: List<Component> = emptyList(),
    val tags: Set<Tag> = emptySet(),
    /**
     * Hierarchy **by reference**; `null` = root (T127-009, FR-011).
     *
     * Storage stays flat: the tree (Workspace → Flow → Tile, Toolbar →
     * ToolbarButton) is *computed* by queries, never nested in the wire format.
     * Same pattern as Bevy / Unity DOTS `Parent` and Android Launcher3's
     * `favorites.container` (research.md R-7).
     */
    val parentId: String? = null,
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
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
    /** The World's rows (renamed from `components` in TASK-136). */
    val entities: List<Entity> = emptyList(),
    val preWizardSnapshot: Profile? = null,
    val snapshotTimestamp: Long? = null,
    val unknownRefs: List<String> = emptyList(),
    val state: ProfileState = ProfileState(),
) {
    /**
     * Set the apply-state of entity [id] by swapping its [LifecycleState]
     * component in the bag (was `mark(id, ComponentStatus)`).
     */
    fun setState(id: String, s: LifecycleState): Profile =
        copy(entities = entities.map { if (it.id == id) it.upsertComponent(s) else it })

    /** Add or replace one component (same-slot) in entity [id]'s bag. */
    fun with(id: String, c: Component): Profile =
        copy(entities = entities.map { if (it.id == id) it.upsertComponent(c) else it })

    /** Remove every component assignable to [type] from entity [id]'s bag. */
    fun without(id: String, type: KClass<out Component>): Profile =
        copy(
            entities = entities.map { e ->
                if (e.id == id) e.copy(components = e.components.filterNot { type.isInstance(it) }) else e
            },
        )

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}

/**
 * The bag "slot" a component occupies. A component replaces another of the same
 * slot on upsert. Every [LifecycleState] variant shares one slot (apply-state is
 * mutually exclusive); every other component keys on its own concrete class.
 *
 * `internal` — shared by [Entity.upsertComponent] here and the `preset/ecs/`
 * compose ops, so both uphold the at-most-one-per-type invariant identically
 * without a model→ecs dependency.
 */
internal fun Component.slotKey(): Any =
    if (this is LifecycleState) LifecycleState::class else this::class

/** Add [c], replacing any existing component in the same [slotKey]. */
internal fun Entity.upsertComponent(c: Component): Entity =
    copy(components = components.filterNot { it.slotKey() == c.slotKey() } + c)
