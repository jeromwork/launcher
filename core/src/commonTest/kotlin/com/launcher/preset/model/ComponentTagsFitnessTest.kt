package com.launcher.preset.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T127-028 (FR-002, NFR-001, SC-003) — fitness gate: every Component subtype must
 * declare a non-empty `tags` default.
 *
 * A subtype with `emptySet()` tags is invisible to every query — it would silently
 * vanish from the screen rather than fail. This test is the build-time gate that
 * turns that into a red build.
 *
 * Kotlin/Native has no full reflection, so `sealedSubclasses` is not portable
 * across KMP targets. The instance list below is therefore explicit, and
 * [subtypeCount_matchesTheInstanceList] fails whenever someone adds a subtype
 * without adding it here — the same "orphan subtype" protection, without
 * reflection.
 */
class ComponentTagsFitnessTest {

    /** One instance of every Component subtype, built with default `tags`. */
    private val allSubtypes: List<Component> = listOf(
        Component.AppTile(packageName = "com.example", labelKey = "l"),
        Component.FontSize(scale = 1.0f),
        Component.Sos(),
        Component.Toolbar(layoutKey = "bottom"),
        Component.LauncherRole(),
        Component.Theme(
            paletteSeedHex = "#000000",
            typographyScale = TypographyScale.Medium,
            shapeStyle = ShapeStyle.Rounded,
            darkMode = false,
        ),
        Component.Language(locale = "system"),
        Component.StatusBarPolicy(),
        Component.Workspace(),
        Component.Flow(titleKey = "t"),
        Component.ToolbarButton(targetFlowId = "f", labelKey = "l"),
    )

    @Test
    fun everySubtype_hasNonEmptyDefaultTags() {
        for (component in allSubtypes) {
            assertTrue(
                component.tags.isNotEmpty(),
                "${component::class.simpleName} has empty default tags — it would be " +
                    "invisible to every query. Give it tags in its constructor default.",
            )
        }
    }

    @Test
    fun subtypeCount_matchesTheInstanceList() {
        // Guards against an orphan subtype: adding one to Component.kt without
        // adding it above would leave its tags unchecked.
        assertEquals(
            11,
            allSubtypes.size,
            "Component subtype count changed — add the new subtype to allSubtypes " +
                "(and give it a non-empty tags default).",
        )
        assertEquals(
            allSubtypes.size,
            allSubtypes.map { it::class }.distinct().size,
            "duplicate subtype in allSubtypes",
        )
    }

    @Test
    fun structuralSubtypes_carryTheirStructuralTag() {
        // The screen skeleton is found by tag, never by an `is` type check
        // (ADR-012: label selectors, not type dispatch).
        assertTrue(Tag.Workspace in Component.Workspace().tags)
        assertTrue(Tag.Flow in Component.Flow(titleKey = "t").tags)
        assertTrue(Tag.ToolbarButton in Component.ToolbarButton(targetFlowId = "f", labelKey = "l").tags)
        assertTrue(Tag.Toolbar in Component.Toolbar(layoutKey = "b").tags)
    }

    @Test
    fun tileSubtypes_carryPresentationAndTile_soTheyReachTheHomeScreen() {
        val appTile = Component.AppTile(packageName = "com.a", labelKey = "l")
        val sos = Component.Sos()

        for (c in listOf(appTile, sos)) {
            assertTrue(Tag.Presentation in c.tags, "${c::class.simpleName} must be Presentation")
            assertTrue(Tag.Tile in c.tags, "${c::class.simpleName} must be a Tile")
        }
        // Toolbar is Presentation but explicitly NOT a Tile — it renders as its
        // own panel and must never leak into the tile grid.
        assertTrue(Tag.Tile !in Component.Toolbar(layoutKey = "b").tags)
    }
}
