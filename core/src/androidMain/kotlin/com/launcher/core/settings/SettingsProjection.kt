package com.launcher.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.launcher.api.settings.LauncherSettings
import com.launcher.api.wireformat.WireFormatJson
import com.launcher.core.diagnostics.RecoveryEventLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * DataStore projection of [LauncherSettings] per FR-033, FR-046, FR-051.
 *
 * Storage key: `com.launcher.settings.banners_v1`. On corruption: emits null;
 * caller ([AndroidSettingsRepository]) substitutes preset-aware defaults via
 * [LauncherSettings.defaultsForPreset] and rewrites the file (single recovery
 * write per FR-051).
 */
class SettingsProjection(
    private val dataStore: DataStore<Preferences>,
    private val logger: RecoveryEventLogger? = null,
) {
    private val json = WireFormatJson.json

    val flow: Flow<LauncherSettings?> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY] ?: return@map null
            try {
                json.decodeFromString(LauncherSettings.serializer(), raw)
            } catch (t: Throwable) {
                logger?.log(
                    RecoveryEventLogger.Category.Corruption,
                    "settings_parse_fail",
                    mapOf("err" to (t.message ?: "unknown").take(40)),
                )
                null
            }
        }
        .catch { t ->
            logger?.log(
                RecoveryEventLogger.Category.Corruption,
                "settings_datastore_read_fail",
                mapOf("err" to (t.message ?: "unknown").take(40)),
            )
            emit(null)
        }

    suspend fun write(settings: LauncherSettings) {
        val raw = json.encodeToString(LauncherSettings.serializer(), settings)
        dataStore.edit { prefs -> prefs[KEY] = raw }
    }

    companion object {
        val KEY = stringPreferencesKey("com.launcher.settings.banners_v1")
    }
}
