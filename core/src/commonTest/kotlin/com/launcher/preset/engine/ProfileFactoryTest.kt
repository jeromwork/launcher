package com.launcher.preset.engine

import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.roundtrip.mvpPool
import com.launcher.preset.roundtrip.simpleLauncherPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileFactoryTest {

    @Test
    fun create_appliesParamsOverride_andResolvesRefs() {
        val pool = mvpPool()
        val preset = simpleLauncherPreset()

        val profile = ProfileFactory().create(preset, pool)

        assertEquals("simple-launcher", profile.basedOnPreset)
        assertEquals(4, profile.components.size)
        val font = profile.components.first { it.id == "font-tile" }
        assertEquals(1.6f, (font.component as Component.FontSize).scale)
        assertTrue(profile.components.all { it.status == ComponentStatus.Pending })
        assertTrue(profile.unknownRefs.isEmpty())
    }

    @Test
    fun create_recordsUnknownRefs_whenPoolMissesRef() {
        val pool = mvpPool()
        val preset = simpleLauncherPreset().copy(
            activeComponents = simpleLauncherPreset().activeComponents +
                com.launcher.preset.model.ActiveComponentEntry("does-not-exist"),
        )
        val profile = ProfileFactory().create(preset, pool)
        assertEquals(listOf("does-not-exist"), profile.unknownRefs)
    }
}
