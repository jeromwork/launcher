package com.launcher.app.preset.task120.facade

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.preset.model.Component
import com.launcher.preset.model.ShapeStyle
import com.launcher.preset.model.TypographyScale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.themePrefs by preferencesDataStore(name = "task126_theme_prefs")

/**
 * T032 — ACL over the app-level theme state (FR-003).
 *
 * Domain sees flat [Component.Theme] values; concrete impl persists them into
 * DataStore. Read by `ThemeProvider.check()`, written by `ThemeProvider.apply()`.
 * The actual Compose theme layer subscribes to this facade (out of scope for
 * this task — Phase 1.6 provides persistence only).
 */
interface AppThemeController {
    suspend fun current(): Component.Theme?
    suspend fun set(theme: Component.Theme)
}

class DataStoreAppThemeController(context: Context) : AppThemeController {
    private val store = context.applicationContext.themePrefs
    private val seedKey = stringPreferencesKey("palette_seed_hex")
    private val typoKey = stringPreferencesKey("typography_scale")
    private val shapeKey = stringPreferencesKey("shape_style")
    private val darkKey = booleanPreferencesKey("dark_mode")

    override suspend fun current(): Component.Theme? =
        store.data.map { prefs ->
            val seed = prefs[seedKey] ?: return@map null
            val typoRaw = prefs[typoKey] ?: return@map null
            val shapeRaw = prefs[shapeKey] ?: return@map null
            val dark = prefs[darkKey] ?: false
            val typo = runCatching { TypographyScale.valueOf(typoRaw) }.getOrNull() ?: return@map null
            val shape = runCatching { ShapeStyle.valueOf(shapeRaw) }.getOrNull() ?: return@map null
            Component.Theme(
                paletteSeedHex = seed,
                typographyScale = typo,
                shapeStyle = shape,
                darkMode = dark,
            )
        }.first()

    override suspend fun set(theme: Component.Theme) {
        store.edit { prefs ->
            prefs[seedKey] = theme.paletteSeedHex
            prefs[typoKey] = theme.typographyScale.name
            prefs[shapeKey] = theme.shapeStyle.name
            prefs[darkKey] = theme.darkMode
        }
    }
}
