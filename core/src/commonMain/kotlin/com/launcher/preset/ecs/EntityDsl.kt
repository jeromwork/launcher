package com.launcher.preset.ecs

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.Tag
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.model.slotKey
import com.launcher.preset.model.upsertComponent

/**
 * Spawn + compose primitives for the own ECS core (TASK-136), mirroring Fleks's
 * `world.entity {}` / `entity[Type]` **vocabulary** so the exit ramp (swap to
 * Fleks) is real. Concrete to our [Component] / [Tag] — no generic component
 * universe (rule 4). Pure Kotlin, zero Android imports (NFR-001).
 *
 * See [contracts/ecs-world-core.md]. LOC budget: the whole `preset/ecs/` package
 * ≤ ~400 LOC (NFR-003, fitness-enforced) — Family DSL + compose ops only, no
 * per-frame system scheduler.
 */

/** Builder for [entity] — mirrors Fleks `world.entity { … }`. */
class EntityBuilder(private val id: String) {
    private val components = mutableListOf<Component>()
    private val tags = mutableSetOf<Tag>()
    private var parentId: String? = null
    var wizardBehavior: WizardBehavior = WizardBehavior.AutoApply
    var critical: Boolean = false

    /** Add one component, upholding at-most-one-per-type (same-slot replaces). */
    fun component(c: Component) {
        components.removeAll { it.slotKey() == c.slotKey() }
        components += c
    }

    fun tag(t: Tag) { tags += t }

    fun parent(id: String?) { parentId = id }

    internal fun build(): Entity = Entity(
        id = id,
        components = components.toList(),
        tags = tags.toSet(),
        parentId = parentId,
        wizardBehavior = wizardBehavior,
        critical = critical,
    )
}

/** Spawn a free-bag [Entity] — mirrors Fleks `world.entity {}`. */
fun entity(id: String, block: EntityBuilder.() -> Unit): Entity =
    EntityBuilder(id).apply(block).build()

/** Typed access — mirrors Fleks `entity[AppTile]`. Returns the component or `null`. */
inline fun <reified T : Component> Entity.get(): T? =
    components.filterIsInstance<T>().firstOrNull()

/** Add [c], replacing any same-slot component (upholds at-most-one-per-type). */
fun Entity.with(c: Component): Entity = upsertComponent(c)

/** Remove every component assignable to [T]. */
inline fun <reified T : Component> Entity.without(): Entity =
    copy(components = components.filterNot { it is T })

fun Entity.withTag(t: Tag): Entity = copy(tags = tags + t)

fun Entity.withoutTag(t: Tag): Entity = copy(tags = tags - t)
