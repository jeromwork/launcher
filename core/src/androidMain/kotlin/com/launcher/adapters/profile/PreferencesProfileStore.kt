package com.launcher.adapters.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.api.preset.PresetRef
import com.launcher.api.profile.PROFILE_STORE_SCHEMA_VERSION
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.ProfileStore
import com.launcher.api.profile.ProfileStoreState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Android adapter for [ProfileStore] using AndroidX DataStore Preferences.
 *
 * Persists the entire [ProfileStoreState] as one JSON string under the key
 * `profile.store.json` (single-blob design — small payloads, atomic writes,
 * no per-field migration ceremony).
 *
 * Map key format: `"<uid>::<version>"` via [PresetRef.toCompositeKey] (R3).
 *
 * Legacy migration (FR-015): if no `profile.store.json` exists AND legacy
 * key `wizard_done=true` is present, synthesize an initial state pointing at
 * `simple-launcher` preset v1. Idempotent — second call sees the synthesized
 * state and skips.
 */
class PreferencesProfileStore(
    private val context: Context,
) : ProfileStore {

    private val json: Json = DEFAULT_JSON

    override suspend fun load(): ProfileStoreState {
        val prefs = context.profileDataStore.data.first()
        val raw = prefs[STORE_KEY]
        if (raw != null) {
            return runCatching { json.decodeFromString(ProfileStoreState.serializer(), raw) }
                .getOrElse { ProfileStoreState() }
        }
        // Legacy migration path (FR-015).
        val migrated = maybeMigrateLegacy(prefs)
        if (migrated != null) {
            save(migrated)
            return migrated
        }
        return ProfileStoreState()
    }

    override suspend fun save(state: ProfileStoreState) {
        val raw = json.encodeToString(ProfileStoreState.serializer(), state)
        context.profileDataStore.edit { it[STORE_KEY] = raw }
    }

    override suspend fun getActive(): Pair<PresetRef, ProfileData>? {
        val state = load()
        val ref = state.activePresetRef ?: return null
        val data = state.profiles[ref.toCompositeKey()] ?: return null
        return ref to data
    }

    override suspend fun putProfile(ref: PresetRef, data: ProfileData) {
        val state = load()
        val updated = state.copy(
            profiles = state.profiles + (ref.toCompositeKey() to data),
        )
        save(updated)
    }

    override suspend fun setActive(ref: PresetRef?) {
        val state = load()
        save(state.copy(activePresetRef = ref))
    }

    /**
     * Test-only seam (T67G MigrationE2ETest): clears the persisted blob and
     * writes the legacy `wizard_done` marker so the next [load] triggers
     * [maybeMigrateLegacy] (FR-015). Production code never calls this.
     */
    suspend fun seedLegacyWizardDoneForTest() {
        context.profileDataStore.edit { prefs ->
            prefs.clear()
            prefs[stringPreferencesKey("wizard_done")] = "true"
        }
    }

    private fun maybeMigrateLegacy(prefs: Preferences): ProfileStoreState? {
        val wizardDone = prefs[LEGACY_WIZARD_DONE]
        if (wizardDone.isNullOrEmpty()) return null
        // Pre-TASK-65 indicator that wizard ran and a preset was selected;
        // we map this to simple-launcher v1 as the canonical default.
        val ref = PresetRef(uid = "com.launcher.preset.simple-launcher", version = 1)
        return ProfileStoreState(
            schemaVersion = PROFILE_STORE_SCHEMA_VERSION,
            activePresetRef = ref,
            profiles = mapOf(ref.toCompositeKey() to ProfileData(
                layout = com.launcher.api.profile.Layout.empty(),
            )),
        )
    }

    private companion object {
        val STORE_KEY = stringPreferencesKey("profile.store.json")
        // legacy keys synthesized by spec-014/015 wizard completion (FR-015).
        val LEGACY_WIZARD_DONE = stringPreferencesKey("wizard_done")

        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "profile_store",
)
