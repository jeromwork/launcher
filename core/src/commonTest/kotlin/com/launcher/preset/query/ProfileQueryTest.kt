package com.launcher.preset.query

import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Tag
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * T127-014 (FR-005, US-2, SC-004) — tag selectors + render gating.
 */
class ProfileQueryTest {

    @Test
    fun byTag_returnsOnlyEntitiesCarryingThatTag() {
        val profile = hierarchicalProfile()

        val flows = profile.byTag(Tag.Flow).map { it.id }

        assertEquals(setOf("flow-calls", "flow-apps", "flow-info"), flows.toSet())
    }

    @Test
    fun byAllTags_requiresEveryTag_AND() {
        val profile = hierarchicalProfile()

        val tiles = profile.byAllTags(setOf(Tag.Presentation, Tag.Tile)).map { it.id }

        // Toolbar carries Presentation but not Tile -> excluded.
        assertEquals(setOf("tile-whatsapp", "sos-primary", "tile-settings"), tiles.toSet())
    }

    @Test
    fun byAnyTag_requiresAtLeastOneTag_OR() {
        val profile = hierarchicalProfile()

        val safety = profile.byAnyTag(setOf(Tag.Safety, Tag.Emergency)).map { it.id }

        assertEquals(listOf("sos-primary"), safety)
    }

    @Test
    fun byNotTag_excludesCarriers() {
        val profile = hierarchicalProfile()

        val notTiles = profile.byNotTag(Tag.Tile).map { it.id }

        assertTrue(notTiles.none { it == "tile-whatsapp" || it == "sos-primary" || it == "tile-settings" })
        assertContains(notTiles, "toolbar-main")
    }

    @Test
    fun multiTagMembership_sosFoundByEachOfItsFourTags() {
        val profile = hierarchicalProfile()

        for (tag in listOf(Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency)) {
            assertContains(profile.byTag(tag).map { it.id }, "sos-primary", "tag $tag should find Sos")
        }
    }

    @Test
    fun unmatchedTag_returnsEmptyList_notNull() {
        val profile = flatProfile()

        assertEquals(emptyList(), profile.byTag(Tag.Emergency))
    }

    @Test
    fun emptyProfile_everySelectorReturnsEmpty() {
        val profile = profileOf()

        assertEquals(emptyList(), profile.byTag(Tag.Presentation))
        assertEquals(emptyList(), profile.homeScreenTiles())
        assertEquals(emptyList(), profile.flows())
        assertEquals(null, profile.toolbar())
        assertEquals(null, profile.workspace())
    }

    @Test
    fun toolbar_isFoundByTag_notByTypeCheck() {
        val profile = hierarchicalProfile()

        val toolbar = profile.toolbar()

        assertNotNull(toolbar)
        assertEquals("toolbar-main", toolbar.id)
        assertTrue(toolbar.component is Component.Toolbar)
        // Toolbar must never leak into the tile grid (Presentation without Tile).
        assertTrue(profile.homeScreenTiles(flowId = "flow-calls").none { it.id == "toolbar-main" })
    }

    // ---- render gating: a dead button must never reach an elderly user ----

    @Test
    fun renderGating_failedAndSkippedTilesAreHidden() {
        val profile = profileOf(
            entity("flow-main", Component.Flow(titleKey = "f")),
            appTile("ok", "com.a", parentId = "flow-main", status = ComponentStatus.Applied),
            appTile("failed", "com.b", parentId = "flow-main", status = ComponentStatus.Failed),
            appTile("skipped", "com.c", parentId = "flow-main", status = ComponentStatus.Skipped),
        )

        val visible = profile.homeScreenTiles().map { it.id }

        assertEquals(listOf("ok"), visible)
    }

    @Test
    fun renderGating_pendingAndUnverifiableTilesAreVisible() {
        val profile = profileOf(
            entity("flow-main", Component.Flow(titleKey = "f")),
            appTile("pending", "com.a", parentId = "flow-main", status = ComponentStatus.Pending),
            appTile("unverifiable", "com.b", parentId = "flow-main", status = ComponentStatus.Unverifiable),
        )

        val visible = profile.homeScreenTiles().map { it.id }

        assertEquals(setOf("pending", "unverifiable"), visible.toSet())
    }
}
