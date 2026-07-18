package com.launcher.preset.model

import com.launcher.preset.engine.ProfileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TASK-136 T136-035 (FR-015e, CL-4) — tag-consistency fitness. Replaces the
 * obsolete `ComponentTagsFitnessTest` (which asserted tags-on-component; tags now
 * live on the entity).
 *
 * Canonical ECS: a tag is a zero-data marker on the **entity**, assigned only
 * **explicitly** — at spawn (the bundle declares them) or by composing code —
 * never auto-derived from a component's type. This gate pins that: an entity's
 * tags equal exactly what was declared, with nothing injected from its components.
 */
class TagConsistencyFitnessTest {

    @Test
    fun entityBuiltWithoutTags_hasNoTags_regardlessOfComponents() {
        // Components in the bag must NOT inject tags — no auto-derivation.
        val e = Entity(
            id = "x",
            components = listOf(
                Component.AppTile(packageName = "com.a", labelKey = "l"),
                LifecycleState.Pending,
            ),
        )
        assertTrue(e.tags.isEmpty(), "tags must not be auto-derived from components (CL-4)")
    }

    @Test
    fun spawnedEntity_carriesExactlyTheBundleTags() {
        val declaredTags = setOf(Tag.Presentation, Tag.Tile, Tag.Communication)
        val pool = Pool(
            declarations = listOf(
                Blueprint(
                    id = "tile-x",
                    components = listOf(Component.AppTile(packageName = "com.x", labelKey = "l")),
                    tags = declaredTags,
                ),
            ),
        )
        val preset = Preset(
            presetId = "p",
            version = 1,
            layoutKey = "single",
            activeComponents = listOf(ActiveComponentEntry("tile-x")),
        )

        val profile = ProfileFactory().create(preset, pool)
        val spawned = profile.entities.single { it.id == "tile-x" }

        assertEquals(
            declaredTags,
            spawned.tags,
            "spawned entity tags must equal the bundle's declared tags exactly — " +
                "no derivation from the component type",
        )
    }
}
