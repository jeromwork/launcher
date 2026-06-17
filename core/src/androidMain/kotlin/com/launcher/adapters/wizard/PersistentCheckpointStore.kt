package com.launcher.adapters.wizard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.api.wizard.WizardCheckpoint
import com.launcher.api.wizard.WizardCheckpointStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Spec 015 — DataStore-backed [WizardCheckpointStore] (FR-006).
 *
 * One Preferences DataStore file per app (`com.launcher.wizard.checkpoint_v1`).
 * Each manifestId stored as a separate JSON-serialized string value.
 *
 * Failure modes:
 *  - Corruption / decode failure → checkpoint treated as absent (load returns null).
 *    Engine re-runs from step 0 — UX regression but not data loss (per FR-003).
 *
 * Migration anchor: `_v1` suffix. Bump on breaking change.
 */
class PersistentCheckpointStore(
    context: Context,
) : WizardCheckpointStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val dataStore: DataStore<Preferences> = context.wizardCheckpointDataStore

    override suspend fun load(manifestId: String): WizardCheckpoint? {
        val prefs = dataStore.data.first()
        val raw = prefs[key(manifestId)] ?: return null
        return try {
            val parsed = json.decodeFromString(WizardCheckpoint.serializer(), raw)
            // Forward-compat policy (FR-003): unknown schemaVersion → treat as absent.
            if (parsed.schemaVersion != 1) null else parsed
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun save(checkpoint: WizardCheckpoint) {
        val encoded = json.encodeToString(WizardCheckpoint.serializer(), checkpoint)
        dataStore.edit { prefs -> prefs[key(checkpoint.manifestId)] = encoded }
    }

    override suspend fun clear(manifestId: String) {
        dataStore.edit { prefs -> prefs.remove(key(manifestId)) }
    }

    private fun key(manifestId: String) = stringPreferencesKey("wizard.checkpoint.$manifestId")
}

private val Context.wizardCheckpointDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "com.launcher.wizard.checkpoint_v1",
)
