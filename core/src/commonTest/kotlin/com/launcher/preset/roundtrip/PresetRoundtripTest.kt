package com.launcher.preset.roundtrip

import com.launcher.preset.model.Preset
import kotlin.test.Test
import kotlin.test.assertEquals

class PresetRoundtripTest {

    @Test
    fun simpleLauncher_roundtrip() = check(simpleLauncherPreset())

    @Test
    fun launcher_roundtrip() = check(launcherPreset())

    @Test
    fun workspace_roundtrip() = check(workspacePreset())

    private fun check(preset: Preset) {
        val encoded = testJson.encodeToString(Preset.serializer(), preset)
        val decoded = testJson.decodeFromString(Preset.serializer(), encoded)
        assertEquals(preset, decoded)
    }
}
