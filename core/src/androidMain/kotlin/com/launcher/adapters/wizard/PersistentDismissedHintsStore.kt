package com.launcher.adapters.wizard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.api.wizard.DismissedHintsState
import com.launcher.api.wizard.DismissedHintsStore
import kotlinx.coroutines.flow.first

class PersistentDismissedHintsStore(
    context: Context,
) : DismissedHintsStore {

    private val dataStore: DataStore<Preferences> = context.dismissedHintsDataStore

    override suspend fun isDismissed(hintId: String): Boolean {
        val prefs = dataStore.data.first()
        return (prefs[KEY_IDS] ?: emptySet()).contains(hintId)
    }

    override suspend fun markDismissed(hintId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_IDS] = (prefs[KEY_IDS] ?: emptySet()) + hintId
        }
    }

    override suspend fun clear(hintId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_IDS] = (prefs[KEY_IDS] ?: emptySet()) - hintId
        }
    }

    override suspend fun current(): DismissedHintsState =
        DismissedHintsState(dataStore.data.first()[KEY_IDS] ?: emptySet())

    companion object {
        private val KEY_IDS = stringSetPreferencesKey("dismissed_hint_ids")
    }
}

private val Context.dismissedHintsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "com.launcher.wizard.dismissed_hints_v1",
)
