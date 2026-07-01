package com.launcher.adapters.wizard

import android.content.Context
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.ConfigSummary
import com.launcher.api.wizard.data.ConfigParser

/**
 * BundledConfigSource — Android adapter that loads the 5 wire formats from
 * `assets/wizard/<kind>/<id>.json`. We use `assets/` rather than Compose
 * Multiplatform Resources `files/` because Compose Resources is still
 * generated-class-only on KMP 1.7.3 (no runtime asset listing API), and we
 * need both `list()` and `load(id)`.
 *
 * Per FR-019, FR-020, FR-021.
 *
 * TODO(shareability): future ConfigSource adapters — FileImportConfigSource,
 * ShareIntentConfigSource, MarketplaceConfigSource (per CLAUDE.md rule 9).
 * The wire-format contract above is identical for all of them.
 *
 * TODO(server-roadmap): when SRV-CONFIG-001 ships — NetworkConfigSource
 * fetches signed configs from our server (per FR-046).
 */
class BundledConfigSource(
    private val context: Context,
) : ConfigSource {

    override suspend fun list(kind: ConfigKind): List<ConfigSummary> {
        val dirName = baseDirFor(kind)
        val files = try {
            context.assets.list(dirName)?.toList().orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        return files
            .filter { it.endsWith(".json") }
            .mapNotNull { fileName ->
                val raw = readAsset("$dirName/$fileName") ?: return@mapNotNull null
                when (val parsed = ConfigParser.parse(kind, raw)) {
                    is ConfigSourceResult.Success -> ConfigSummary(
                        id = parsed.document.header.id,
                        nameKey = parsed.document.header.name,
                        descriptionKey = parsed.document.header.description,
                        deviceClass = parsed.document.header.deviceClass,
                    )
                    else -> null
                }
            }
    }

    override suspend fun load(kind: ConfigKind, id: String): ConfigSourceResult {
        val dirName = baseDirFor(kind)
        val files = try {
            context.assets.list(dirName)?.toList().orEmpty()
        } catch (_: Throwable) {
            return ConfigSourceResult.NotFound(id)
        }
        for (fileName in files) {
            if (!fileName.endsWith(".json")) continue
            val raw = readAsset("$dirName/$fileName") ?: continue
            val parsed = ConfigParser.parse(kind, raw)
            if (parsed is ConfigSourceResult.Success && parsed.document.header.id == id) {
                return parsed
            }
            if (parsed is ConfigSourceResult.IncompatibleVersion) {
                // bubble up — caller routes to Play Store fallback (FR-016).
                return parsed
            }
        }
        return ConfigSourceResult.NotFound(id)
    }

    private fun baseDirFor(kind: ConfigKind): String = when (kind) {
        ConfigKind.WizardManifest -> "wizard/wizard-manifests"
        ConfigKind.ScreenLayout -> "wizard/screen-layouts"
        ConfigKind.TileSet -> "wizard/tile-sets"
        ConfigKind.SystemSettingsPool -> "wizard/system-settings"
        ConfigKind.UICustomizationPool -> "wizard/ui-customization"
        ConfigKind.Preset -> "presets"
    }

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).use { it.readBytes().decodeToString() }
    } catch (_: Throwable) {
        null
    }
}
