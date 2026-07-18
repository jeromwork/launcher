package com.launcher.preset.engine

import com.launcher.preset.ecs.get
import com.launcher.preset.model.Component
import com.launcher.preset.model.LifecycleState
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
        assertEquals(4, profile.entities.size)
        val font = profile.entities.first { it.id == "font-tile" }
        assertEquals(1.6f, font.get<Component.FontSize>()!!.scale)
        assertTrue(profile.entities.all { it.get<LifecycleState>() == LifecycleState.Pending })
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
