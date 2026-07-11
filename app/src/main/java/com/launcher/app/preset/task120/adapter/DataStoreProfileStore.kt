package com.launcher.app.preset.task120.adapter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.preset.model.Profile
import com.launcher.preset.port.ProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.profileTask120Store by preferencesDataStore(name = "task120_profile")

/**
 * DataStore-backed ProfileStore. Stores profile as a JSON blob under a single
 * preference key. Migration writers for future schemaVersion bumps live here.
 */
class DataStoreProfileStore(context: Context) : ProfileStore {
    private val store = context.applicationContext.profileTask120Store
    private val profileKey = stringPreferencesKey("profile_json_v2")
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observe(): Flow<Profile?> = store.data.map { prefs ->
        prefs[profileKey]?.let { json.decodeFromString(Profile.serializer(), it) }
    }

    override suspend fun load(): Profile? = observe().first()

    override suspend fun save(profile: Profile) {
        store.edit { it[profileKey] = json.encodeToString(Profile.serializer(), profile) }
    }

    override suspend fun setPreWizardSnapshot(snapshot: Profile) {
        val current = load() ?: return
        val stripped = snapshot.copy(preWizardSnapshot = null)
        save(current.copy(preWizardSnapshot = stripped, snapshotTimestamp = System.currentTimeMillis()))
    }

    override suspend fun restoreFromPreWizardSnapshot(): Profile? {
        val current = load() ?: return null
        val snapshot = current.preWizardSnapshot ?: return current
        save(snapshot)
        return snapshot
    }
}
