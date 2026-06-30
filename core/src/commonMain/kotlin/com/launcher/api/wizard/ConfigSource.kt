package com.launcher.api.wizard

import com.launcher.api.wizard.data.ConfigDocument

/**
 * Wizard config loader port — per FR-019.
 *
 * Loads the 5 bundled wire formats:
 *   1. wizard.manifest
 *   2. screen.layout
 *   3. tile.set
 *   4. system-settings.pool
 *   5. ui-customization.pool
 *
 * TODO(shareability): future ConfigSource adapters — file import, share
 * intent, marketplace. Bundled is one of many.
 * TODO(server-roadmap): NetworkConfigSource → own server (FR-046).
 */
interface ConfigSource {
    suspend fun list(kind: ConfigKind): List<ConfigSummary>
    suspend fun load(kind: ConfigKind, id: String): ConfigSourceResult
}

enum class ConfigKind {
    WizardManifest,
    ScreenLayout,
    TileSet,
    SystemSettingsPool,
    UICustomizationPool,
    Preset,
}

sealed class ConfigSourceResult {
    data class Success(val document: ConfigDocument) : ConfigSourceResult()
    data class IncompatibleVersion(val found: Int, val known: Int) : ConfigSourceResult()
    data class ParseError(val reason: String) : ConfigSourceResult()
    data class NotFound(val id: String) : ConfigSourceResult()
}

data class ConfigSummary(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
    val deviceClass: List<String>,
)
