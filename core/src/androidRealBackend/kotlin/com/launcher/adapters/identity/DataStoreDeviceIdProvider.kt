package com.launcher.adapters.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.api.identity.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.UUID

/**
 * DataStore-backed [DeviceIdProvider] for the realBackend flavor (FR-001).
 *
 * The id is **generated once on first access and never deleted**, even on
 * revoke — it must survive across pairings so a Managed device that revokes
 * and re-pairs is still recognised as the same physical device (which
 * matters for `/devices/{managedDeviceId}` records and future analytics).
 *
 * Schema: `com.launcher.pairing.identity_v1` per data-model.md §Persistence.
 * The `_v1` suffix is the migration anchor — when we need a breaking change
 * to the persistence shape, bump to `_v2` and write a one-shot migration
 * before the first read of the new key.
 *
 * TODO(OWD-2 named auth): when `linkWithCredential` lands, this UUID remains
 * the stable id; the Firebase Auth UID becomes named-anchored. Comment in
 * the persistence section of data-model.md §Persistence.
 */
class DataStoreDeviceIdProvider(
    private val context: Context,
) : DeviceIdProvider {

    private val dataStore: DataStore<Preferences> = context.identityDataStore

    override fun currentDeviceId(): Flow<String> = dataStore.data
        .map { prefs -> prefs[KEY_DEVICE_ID] ?: "" }
        .onEach { id ->
            if (id.isEmpty()) {
                // Lazy-init on first read. Concurrent collectors get the
                // same value via DataStore's read-modify-write semantics.
                ensureDeviceId()
            }
        }
        .map { it.ifEmpty { dataStore.data.first()[KEY_DEVICE_ID] ?: "" } }

    override suspend fun regenerate() {
        dataStore.edit { it[KEY_DEVICE_ID] = UUID.randomUUID().toString() }
    }

    private suspend fun ensureDeviceId() {
        dataStore.edit { prefs ->
            if (prefs[KEY_DEVICE_ID].isNullOrEmpty()) {
                prefs[KEY_DEVICE_ID] = UUID.randomUUID().toString()
            }
        }
    }

    companion object {
        private val KEY_DEVICE_ID = stringPreferencesKey("managedDeviceId")
    }
}

private val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "com.launcher.pairing.identity_v1",
)
