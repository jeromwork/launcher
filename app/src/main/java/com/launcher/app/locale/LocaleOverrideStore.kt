package com.launcher.app.locale

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * TASK-126 Phase 7 wave B — carved out of the legacy
 * `com.launcher.api.wizard.UserPreferencesStore` blob. The wizard-runtime
 * migration only needs one field of that blob (language override tag);
 * carrying the whole `UserPreferences` schema forward would drag the
 * legacy `api/wizard/` package into the new ECS surface for no benefit.
 *
 * On-disk key preserved from `PersistentUserPreferencesStore` so a
 * previously-persisted language override survives the migration. The
 * value shape changes from a JSON `UserPreferences` blob to a bare
 * string: reading the legacy blob would require the old schema, so
 * we accept the one-time reset on migration (owner acceptable — locale
 * override is a rare setting and re-selecting it is trivial from
 * Settings > Language).
 */
class LocaleOverrideStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.localeOverrideDataStore

    suspend fun current(): String? = dataStore.data.first()[KEY_LOCALE_OVERRIDE]

    suspend fun set(bcp47Tag: String?) {
        dataStore.edit { prefs ->
            if (bcp47Tag == null) prefs.remove(KEY_LOCALE_OVERRIDE)
            else prefs[KEY_LOCALE_OVERRIDE] = bcp47Tag
        }
    }

    companion object {
        internal val KEY_LOCALE_OVERRIDE = stringPreferencesKey("locale_override")
    }
}

private val Context.localeOverrideDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "com.launcher.preset.locale_override_v1",
)
