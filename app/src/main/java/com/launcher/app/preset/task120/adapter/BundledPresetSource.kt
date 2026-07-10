package com.launcher.app.preset.task120.adapter

import android.content.Context
import com.launcher.preset.model.Preset
import com.launcher.preset.port.PresetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Reads preset JSON files from `assets/preset/bundled-presets/`.
 *
 * TODO(shareability): future PresetSource adapters — file import (Intent.ACTION_VIEW),
 * share intent (ACTION_SEND), network fetch, QR-scan. Add as additive adapters
 * without wire format change.
 */
class BundledPresetSource(
    private val context: Context,
    private val bundledDir: String = "preset/bundled-presets",
) : PresetSource {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    override suspend fun loadPreset(presetId: String): Preset? = withContext(Dispatchers.IO) {
        val filename = "$bundledDir/$presetId.json"
        try {
            val text = context.assets.open(filename).bufferedReader().use { it.readText() }
            json.decodeFromString(Preset.serializer(), text)
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun listAvailable(): List<String> = withContext(Dispatchers.IO) {
        (context.assets.list(bundledDir) ?: emptyArray())
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }
    }
}
