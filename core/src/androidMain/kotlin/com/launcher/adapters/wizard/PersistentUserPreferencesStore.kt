package com.launcher.adapters.wizard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.api.wizard.UserPreferences
import com.launcher.api.wizard.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class PersistentUserPreferencesStore(
    context: Context,
) : UserPreferencesStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val dataStore: DataStore<Preferences> = context.userPreferencesDataStore

    override suspend fun save(prefs: UserPreferences) {
        val encoded = json.encodeToString(UserPreferences.serializer(), prefs)
        dataStore.edit { it[KEY_PAYLOAD] = encoded }
    }

    override fun observe(): Flow<UserPreferences> = dataStore.data
        .map { decode(it[KEY_PAYLOAD]) }
        .distinctUntilChanged()

    override suspend fun current(): UserPreferences = decode(dataStore.data.first()[KEY_PAYLOAD])

    override suspend fun markWizardCompleted(presetId: String) {
        val now = current()
        save(now.copy(wizardCompletedPresets = now.wizardCompletedPresets + presetId))
    }

    override suspend fun isWizardCompleted(presetId: String): Boolean =
        presetId in current().wizardCompletedPresets

    private fun decode(raw: String?): UserPreferences {
        if (raw.isNullOrBlank()) return UserPreferences()
        return try {
            val parsed = json.decodeFromString(UserPreferences.serializer(), raw)
            if (parsed.schemaVersion != 1) UserPreferences() else parsed
        } catch (_: Exception) {
            UserPreferences()
        }
    }

    companion object {
        private val KEY_PAYLOAD = stringPreferencesKey("user_preferences_payload")
    }
}

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "com.launcher.wizard.user_preferences_v1",
)
