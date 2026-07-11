package com.launcher.preset.engine

import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.ChangeItem
import com.launcher.preset.model.Component
import com.launcher.preset.roundtrip.mvpPool
import com.launcher.preset.roundtrip.simpleLauncherPreset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PresetDiffTest {

    @Test
    fun added_removed_paramsChanged_classifications() {
        val pool = mvpPool()
        val current = simpleLauncherPreset()
        val incoming = current.copy(
            version = current.version + 1,
            activeComponents = listOf(
                ActiveComponentEntry("sos-main"),
                ActiveComponentEntry("tile-whatsapp"),
                ActiveComponentEntry("toolbar-minimal"),
                ActiveComponentEntry(
                    "font-tile",
                    JsonObject(mapOf("scale" to JsonPrimitive(1.2f))),
                ),
            ),
        )
        val changes = PresetDiff().diff(current, incoming, pool)
        val paramsChanged = changes.filterIsInstance<ChangeItem.ParamsChanged>()
        assertEquals(1, paramsChanged.size)
        assertEquals("font-tile", paramsChanged.single().id)
        assertEquals(1.2f, (paramsChanged.single().newComponent as Component.FontSize).scale)
    }

    @Test
    fun added_appearWhenRefIntroduced() {
        val pool = mvpPool()
        val current = simpleLauncherPreset().copy(
            activeComponents = listOf(ActiveComponentEntry("font-tile")),
        )
        val incoming = current.copy(
            version = current.version + 1,
            activeComponents = current.activeComponents + ActiveComponentEntry("sos-main"),
        )
        val changes = PresetDiff().diff(current, incoming, pool)
        val added = changes.filterIsInstance<ChangeItem.Added>()
        assertEquals(1, added.size)
        assertEquals("sos-main", added.single().id)
    }

    @Test
    fun removed_appearWhenRefDropped() {
        val pool = mvpPool()
        val current = simpleLauncherPreset()
        val incoming = current.copy(
            version = current.version + 1,
            activeComponents = current.activeComponents.filterNot { it.poolRef == "toolbar-minimal" },
        )
        val changes = PresetDiff().diff(current, incoming, pool)
        val removed = changes.filterIsInstance<ChangeItem.Removed>()
        assertEquals(1, removed.size)
        assertEquals("toolbar-minimal", removed.single().id)
    }

    @Test
    fun sameVersionDifferentContent_rejected() {
        val pool = mvpPool()
        val current = simpleLauncherPreset()
        val incoming = current.copy(
            activeComponents = current.activeComponents.filterNot { it.poolRef == "toolbar-minimal" },
        )
        assertFailsWith<IllegalStateException> {
            PresetDiff().diff(current, incoming, pool)
        }
    }

    @Test
    fun noChanges_emptyList() {
        val pool = mvpPool()
        val current = simpleLauncherPreset()
        val incoming = current.copy(version = current.version + 1)
        assertTrue(PresetDiff().diff(current, incoming, pool).isEmpty())
    }
}
