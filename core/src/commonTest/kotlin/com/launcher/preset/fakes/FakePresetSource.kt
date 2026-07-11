package com.launcher.preset.fakes

import com.launcher.preset.model.Preset
import com.launcher.preset.port.PresetSource

class FakePresetSource(private val presets: Map<String, Preset>) : PresetSource {
    override suspend fun loadPreset(presetId: String): Preset? = presets[presetId]
    override suspend fun listAvailable(): List<String> = presets.keys.toList()
}
