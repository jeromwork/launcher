package com.launcher.app.preset.task120.facade

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.uiPrefs by preferencesDataStore(name = "task120_ui_prefs")

/**
 * ACL over UI-scoped preferences (font scale, etc.). Domain sees Float / suspend.
 */
interface UiPrefsFacade {
    suspend fun fontScale(): Float
    suspend fun setFontScale(f: Float)
}

class DataStoreUiPrefsFacade(context: Context) : UiPrefsFacade {
    private val store = context.applicationContext.uiPrefs
    private val fontKey = floatPreferencesKey("font_scale")

    override suspend fun fontScale(): Float =
        store.data.map { it[fontKey] ?: 1.0f }.first()

    override suspend fun setFontScale(f: Float) {
        store.edit { it[fontKey] = f }
    }
}
