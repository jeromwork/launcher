package com.launcher.app.data.recovery

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import family.keys.api.SchemaVersionMemory
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed [SchemaVersionMemory] (T122m, H-2 mitigation, FR-028b).
 *
 * Persistent TOLU per (uid, blobKind). Stores `max(stored, fetched)` всегда —
 * monotonically increasing invariant.
 *
 * **Storage**: `datastore/tolu_schema_versions_v1.preferences_pb`. Excluded из
 * cloud-backup (data_extraction_rules.xml) — иначе restore из backup может
 * понизить lastSeenVersion и open rollback attack window.
 *
 * **Cleared при Sign-Out** — это accepted, re-trust on first read для new identity
 * session.
 */
class DataStoreSchemaVersionMemory(
    private val context: Context
) : SchemaVersionMemory {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    override suspend fun recordSeenVersion(uid: String, blobKind: String, version: Int) {
        context.dataStore.edit { prefs ->
            val cur = prefs[key(uid, blobKind)] ?: 0
            if (version > cur) {
                prefs[key(uid, blobKind)] = version
            }
        }
    }

    override suspend fun lastSeenVersion(uid: String, blobKind: String): Int? {
        val prefs = context.dataStore.data.first()
        return prefs[key(uid, blobKind)]
    }

    private fun key(uid: String, blobKind: String): Preferences.Key<Int> =
        intPreferencesKey("tolu_${uid}_$blobKind")

    companion object {
        const val DATASTORE_NAME: String = "tolu_schema_versions_v1"
    }
}
