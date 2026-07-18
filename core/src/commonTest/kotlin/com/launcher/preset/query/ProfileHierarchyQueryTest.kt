package com.launcher.preset.query

import com.launcher.preset.ecs.get
import com.launcher.preset.model.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * T127-015 (FR-012, US-4, SC-009) — hierarchy selectors over flat storage.
 *
 * The tree is never nested in the data; every assertion here proves it can be
 * reconstructed from `parentId` alone.
 */
class ProfileHierarchyQueryTest {

    @Test
    fun roots_returnsOnlyParentlessEntities() {
        val profile = hierarchicalProfile()

        val roots = profile.roots().map { it.id }

        // ws-main is the screen root; statusbar is a root-level system setting.
        assertEquals(setOf("ws-main", "statusbar"), roots.toSet())
    }

    @Test
    fun children_returnsDirectChildrenOnly_notGrandchildren() {
        val profile = hierarchicalProfile()

        val children = profile.children("ws-main").map { it.id }

        assertEquals(setOf("flow-calls", "flow-apps", "flow-info", "toolbar-main"), children.toSet())
        // Tiles live under the flows, not under the workspace.
        assertTrue(children.none { it == "tile-whatsapp" })
    }

    @Test
    fun workspace_returnsTheScreenRoot() {
        val profile = hierarchicalProfile()

        val ws = profile.workspace()

        assertNotNull(ws)
        assertEquals("ws-main", ws.id)
        assertNotNull(ws.get<Component.Workspace>())
    }

    @Test
    fun flows_areOrderedByOrderField_notByInsertionOrder() {
        val profile = hierarchicalProfile()

        val flows = profile.flows().map { it.id }

        // Fixture inserts apps(1) before calls(0) on purpose.
        assertEquals(listOf("flow-calls", "flow-apps", "flow-info"), flows)
    }

    @Test
    fun tilesOf_isolatesTilesPerFlow() {
        val profile = hierarchicalProfile()

        assertEquals(setOf("tile-whatsapp", "sos-primary"), profile.tilesOf("flow-calls").map { it.id }.toSet())
        assertEquals(listOf("tile-settings"), profile.tilesOf("flow-apps").map { it.id })
        assertEquals(emptyList(), profile.tilesOf("flow-info"))
    }

    @Test
    fun toolbarButtons_areOrdered_andEachTargetsAnExistingFlow() {
        val profile = hierarchicalProfile()

        val buttons = profile.toolbarButtons()

        assertEquals(listOf("btn-calls", "btn-apps", "btn-info"), buttons.map { it.id })
        val flowIds = profile.flows().map { it.id }.toSet()
        for (b in buttons) {
            val target = b.get<Component.ToolbarButton>()!!.targetFlowId
            assertTrue(target in flowIds, "button ${b.id} targets unknown flow $target")
        }
    }

    @Test
    fun toolbarButtons_areChildrenOfTheToolbar() {
        val profile = hierarchicalProfile()

        val toolbarId = profile.toolbar()!!.id

        assertEquals(
            profile.toolbarButtons().map { it.id }.toSet(),
            profile.children(toolbarId).map { it.id }.toSet(),
        )
    }

    @Test
    fun orphanEntity_isSilentlyAbsentFromChildren_noCrash() {
        val profile = profileOf(
            entity("flow-main", Component.Flow(titleKey = "f")),
            appTile("orphan", "com.a", parentId = "flow-that-does-not-exist"),
        )

        // A dangling parentId is reported by assembly-time validation (FR-016),
        // never by a runtime crash here.
        assertEquals(emptyList(), profile.children("flow-main"))
        assertEquals(emptyList(), profile.tilesOf("flow-main"))
    }

    @Test
    fun homeScreenTiles_defaultsToFirstFlow() {
        val profile = hierarchicalProfile()

        val tiles = profile.homeScreenTiles().map { it.id }

        assertEquals(setOf("tile-whatsapp", "sos-primary"), tiles.toSet())
    }

    @Test
    fun homeScreenTiles_withExplicitFlowId_selectsThatFlow() {
        val profile = hierarchicalProfile()

        assertEquals(listOf("tile-settings"), profile.homeScreenTiles(flowId = "flow-apps").map { it.id })
    }

    @Test
    fun degenerateProfile_withoutFlows_returnsAllTiles_sameCodePath() {
        val profile = flatProfile()

        val tiles = profile.homeScreenTiles().map { it.id }

        assertEquals(setOf("tile-settings", "tile-phone"), tiles.toSet())
        assertEquals(emptyList(), profile.flows())
        assertEquals(null, profile.toolbar())
    }
}
