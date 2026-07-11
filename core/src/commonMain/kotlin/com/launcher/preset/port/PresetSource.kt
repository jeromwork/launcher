package com.launcher.preset.port

import com.launcher.preset.model.Preset

interface PresetSource {
    suspend fun loadPreset(presetId: String): Preset?
    suspend fun listAvailable(): List<String>

    // TODO(shareability): future PresetSource adapters — file import (Intent.ACTION_VIEW),
    // share intent (ACTION_SEND), network fetch, QR-scan. Add as additive adapters without
    // wire format change.
}
