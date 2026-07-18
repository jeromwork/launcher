package com.launcher.preset.ecs

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.ShapeStyle
import com.launcher.preset.model.Tag
import com.launcher.preset.model.TypographyScale
import com.launcher.preset.query.byAllTags
import com.launcher.preset.query.byAnyTag
import com.launcher.preset.query.profileOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TASK-136 T136-028 (SC-001, FR-006, FR-007) — Capability Story 1: an entity is a
 * free bag; tags compose, and multiple data components compose on one entity.
 *
 * Note: `Component` is a `sealed interface`, so a test-only fake subtype cannot be
 * declared in the test module. Data-level composition is therefore exercised with
 * two **real** component types ([Component.AppTile] + [Component.Theme]) on one
 * entity — a genuine demonstration of the canonical model's intrinsic capability.
 */
class EntityCompositionTest {

    private fun theme() = Component.Theme(
        paletteSeedHex = "#101010",
        typographyScale = TypographyScale.Large,
        shapeStyle = ShapeStyle.Rounded,
        darkMode = true,
    )

    @Test
    fun entityCarriesSeveralTagsAtOnce_foundByAnyValidCombination() {
        val e = Entity(
            id = "tile-sos",
            components = listOf(Component.Sos()),
            tags = setOf(Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency),
        )
        val profile = profileOf(e)

        assertEquals(listOf(e), profile.byAllTags(setOf(Tag.Tile, Tag.Safety)))
        assertEquals(listOf(e), profile.byAllTags(setOf(Tag.Presentation, Tag.Emergency)))
        assertEquals(listOf(e), profile.byAnyTag(setOf(Tag.Emergency, Tag.System)))
        assertTrue(profile.byAllTags(setOf(Tag.Tile, Tag.System)).isEmpty())
    }

    @Test
    fun multipleDataComponentsComposeOnOneEntity_getFindsEach() {
        val base = Entity(
            id = "e",
            components = listOf(
                Component.AppTile(packageName = "com.a", labelKey = "l"),
                LifecycleState.Pending,
            ),
        )

        // Add a second data component of a different type — canonical composition.
        val composed = base.with(theme())

        assertNotNull(composed.get<Component.AppTile>(), "base component still present")
        assertNotNull(composed.get<Component.Theme>(), "added component present")
        assertNotNull(composed.get<LifecycleState>(), "state component untouched")
        assertEquals("com.a", composed.get<Component.AppTile>()?.packageName)
        // A type not in the bag returns null — no manual cast needed.
        assertNull(composed.get<Component.FontSize>())
    }

    @Test
    fun withReplacesSameType_upholdingAtMostOnePerType() {
        val e = Entity(id = "e", components = listOf(Component.FontSize(1.0f)))
            .with(Component.FontSize(2.0f))
        assertEquals(1, e.components.count { it is Component.FontSize })
        assertEquals(2.0f, e.get<Component.FontSize>()?.scale)
    }

    @Test
    fun withoutRemovesType() {
        val e = Entity(
            id = "e",
            components = listOf(Component.AppTile("com.a", "l"), LifecycleState.Applied),
        ).without<LifecycleState>()
        assertNull(e.get<LifecycleState>())
        assertNotNull(e.get<Component.AppTile>())
    }
}
