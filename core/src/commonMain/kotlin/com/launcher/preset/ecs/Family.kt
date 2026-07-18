package com.launcher.preset.ecs

import com.launcher.preset.model.Entity
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Tag

/**
 * Query matcher over the World's entity bag — mirrors Fleks
 * `world.family { all/any/none }`. Tag-based (the canonical zero-data markers);
 * `ProfileQuery` named selectors are thin wrappers over this + [get].
 *
 * Linear scan over `Profile.entities` (~20–40, < 1 ms, NFR-002).
 */
class FamilyBuilder {
    private val all = mutableSetOf<Tag>()
    private val any = mutableSetOf<Tag>()
    private val none = mutableSetOf<Tag>()

    /** Entity must carry ALL of [t]. */
    fun all(vararg t: Tag) { all += t }

    /** Entity must carry AT LEAST ONE of [t]. */
    fun any(vararg t: Tag) { any += t }

    /** Entity must carry NONE of [t]. */
    fun none(vararg t: Tag) { none += t }

    internal fun matches(e: Entity): Boolean {
        if (!e.tags.containsAll(all)) return false
        if (any.isNotEmpty() && any.none { it in e.tags }) return false
        if (none.any { it in e.tags }) return false
        return true
    }
}

/** Select entities matching the built family — mirrors Fleks `world.family { … }`. */
fun Profile.family(block: FamilyBuilder.() -> Unit): List<Entity> {
    val matcher = FamilyBuilder().apply(block)
    return entities.filter(matcher::matches)
}
