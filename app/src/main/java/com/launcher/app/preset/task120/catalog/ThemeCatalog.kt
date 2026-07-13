package com.launcher.app.preset.task120.catalog

import android.content.Context
import com.launcher.preset.model.Component
import com.launcher.preset.model.ShapeStyle
import com.launcher.preset.model.TypographyScale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * T036 — ThemeCatalog (FR-003, D3).
 *
 * Write-time sugar: expands a symbolic `ThemeRef(name)` (used e.g. in preset
 * authoring tools / bundled preset templates) into a flat [Component.Theme]
 * with all fields present. The wire-format `Preset` never carries a `ThemeRef`
 * — only flat Theme fields — per D3 in `design.md`. This catalog is consulted
 * once at save/import time; runtime code sees only flat Theme.
 *
 * Reads `assets/theme-catalog.json` lazily on first `resolve()` call.
 *
 * TODO(shareability): future ThemeCatalog sources — file import, marketplace.
 * Additional adapters plug in additively without changing wire format
 * (CLAUDE.md rule 9).
 */
class ThemeCatalog(
    private val context: Context,
    private val assetPath: String = "theme-catalog.json",
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val entries: Map<String, Component.Theme> by lazy { loadEntries() }

    /**
     * Returns a flat [Component.Theme] for the given catalog name, or `null`
     * if the name is unknown. Callers MUST treat `null` as a validation error
     * before persistence (T044).
     */
    fun resolve(ref: String): Component.Theme? = entries[ref]

    /** Names available in this catalog — useful for author-tooling UIs. */
    fun availableNames(): Set<String> = entries.keys

    private fun loadEntries(): Map<String, Component.Theme> {
        val text = try {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            return emptyMap()
        }
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val themesArr = root["themes"]?.jsonArray ?: return emptyMap()
            themesArr.mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val seed = obj["paletteSeedHex"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val typoRaw = obj["typographyScale"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val shapeRaw = obj["shapeStyle"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val darkMode = obj["darkMode"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                val typo = runCatching { TypographyScale.valueOf(typoRaw) }.getOrNull()
                    ?: return@mapNotNull null
                val shape = runCatching { ShapeStyle.valueOf(shapeRaw) }.getOrNull()
                    ?: return@mapNotNull null
                name to Component.Theme(
                    paletteSeedHex = seed,
                    typographyScale = typo,
                    shapeStyle = shape,
                    darkMode = darkMode,
                )
            }.toMap()
        } catch (t: Throwable) {
            emptyMap()
        }
    }
}
