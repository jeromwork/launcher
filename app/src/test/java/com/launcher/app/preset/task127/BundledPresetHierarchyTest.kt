package com.launcher.app.preset.task127

import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.model.Component
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.query.flows
import com.launcher.preset.query.tilesOf
import com.launcher.preset.query.toolbar
import com.launcher.preset.query.toolbarButtons
import com.launcher.preset.query.workspace
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T127-025 / T127-026 (FR-013, US-4) — the **shipped** pool.json + bundled
 * presets must assemble into a valid hierarchy.
 *
 * Reads the real asset files rather than in-memory fixtures: a pool/preset
 * mismatch (a `poolRef` or `parentRef` typo) would otherwise only surface on a
 * device, as a half-rendered home screen. This is the cheap gate that keeps a
 * broken preset from ever reaching the owner (see also TASK-132).
 */
class BundledPresetHierarchyTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    private val factory = ProfileFactory()

    private fun asset(path: String): String {
        val file = File("src/main/assets/$path")
        assertTrue("missing bundled asset: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    private fun pool(): Pool = json.decodeFromString(Pool.serializer(), asset("preset/pool.json"))

    private fun preset(id: String): Preset =
        json.decodeFromString(Preset.serializer(), asset("preset/bundled-presets/$id.json"))

    @Test
    fun bundledPool_declaresStructuralBlueprints() {
        val ids = pool().declarations.map { it.id }

        assertTrue("pool must declare a workspace", ids.contains("ws-main"))
        assertTrue("pool must declare a flow", ids.contains("flow-main"))
        assertTrue("pool must declare a toolbar button", ids.contains("btn-main"))
    }

    @Test
    fun simpleLauncher_assemblesIntoValidHierarchy() {
        // simple-launcher is the preset PresetBootstrap activates on first run,
        // i.e. exactly what a fresh install gets.
        val profile = factory.create(preset("simple-launcher"), pool())

        assertEquals(
            "preset references a pool id that does not exist",
            emptyList<String>(),
            profile.unknownRefs,
        )
        assertEquals(
            "bundled preset must assemble into a structurally valid tree",
            emptyList<Any>(),
            factory.validateHierarchy(profile),
        )
    }

    @Test
    fun simpleLauncher_producesRenderableScreen() {
        val profile = factory.create(preset("simple-launcher"), pool())

        val workspace = profile.workspace()
        assertNotNull("home screen needs a workspace root", workspace)

        val flows = profile.flows()
        assertTrue("home screen needs at least one flow", flows.isNotEmpty())

        // The regression this whole task exists for: a fresh install must land on
        // tiles, not on an empty screen.
        val tiles = profile.tilesOf(flows.first().id)
        assertTrue("first flow must carry at least one tile", tiles.isNotEmpty())
    }

    @Test
    fun simpleLauncher_toolbarButtonsTargetRealFlows() {
        val profile = factory.create(preset("simple-launcher"), pool())

        assertNotNull(profile.toolbar())
        val flowIds = profile.flows().map { it.id }.toSet()
        val buttons = profile.toolbarButtons()

        assertTrue("toolbar must have buttons", buttons.isNotEmpty())
        for (button in buttons) {
            val target = (button.component as Component.ToolbarButton).targetFlowId
            assertTrue("button ${button.id} targets unknown flow $target", target in flowIds)
        }
    }

    @Test
    fun launcherPreset_assemblesIntoValidHierarchy_withTwoFlows() {
        val profile = factory.create(preset("launcher"), pool())

        assertEquals(emptyList<String>(), profile.unknownRefs)
        assertEquals(emptyList<Any>(), factory.validateHierarchy(profile))
        assertEquals(2, profile.flows().size)
        // Each flow owns its own tiles — no bleed-through.
        for (flow in profile.flows()) {
            assertTrue("flow ${flow.id} should carry tiles", profile.tilesOf(flow.id).isNotEmpty())
        }
    }

    @Test
    fun workspacePreset_stillAssembles_evenIfFlat() {
        // Not yet migrated to the hierarchy — must keep working as a degenerate
        // (all-roots) profile rather than break.
        val profile = factory.create(preset("workspace"), pool())

        assertEquals(emptyList<Any>(), factory.validateHierarchy(profile))
    }
}
