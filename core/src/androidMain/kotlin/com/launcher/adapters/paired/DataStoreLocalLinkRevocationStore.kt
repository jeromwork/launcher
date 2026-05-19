package com.launcher.adapters.paired

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.api.paired.LocalLinkRevocationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Spec 010 T081 — DataStore-backed [LocalLinkRevocationStore] (FR-032).
 *
 * Persists the revoked-link-id set to a Preferences DataStore file
 * `com.launcher.paired.revocation_v1`. The `_v1` suffix is the migration
 * anchor: when the shape needs to change, bump to `_v2` and write a one-shot
 * migration before the first read of the new key.
 *
 * Schema choice rationale:
 *  - `Set<String>` Preferences key fits the access pattern (full-set reads
 *    on enqueue, single-id flips on mark/clear). The alternative — DataStore
 *    Proto — was rejected because the data is a flat set с no migration
 *    pressure beyond add/remove.
 *
 * Failure mode: read failures (corruption) surface as an empty set on the
 * upstream Flow rather than a crash, because losing the flag is a UX
 * regression (Маша reappears) but не security incident — server-side
 * deactivate is the authoritative cleanup (FR-032a path (c)).
 */
class DataStoreLocalLinkRevocationStore(
    context: Context,
) : LocalLinkRevocationStore {

    private val dataStore: DataStore<Preferences> = context.revocationDataStore

    override suspend fun markRevoked(linkId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_REVOKED_IDS] ?: emptySet()
            prefs[KEY_REVOKED_IDS] = current + linkId
        }
    }

    override fun isRevoked(linkId: String): Flow<Boolean> = dataStore.data
        .map { prefs -> (prefs[KEY_REVOKED_IDS] ?: emptySet()).contains(linkId) }
        .distinctUntilChanged()

    override suspend fun clearRevoked(linkId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_REVOKED_IDS] ?: emptySet()
            prefs[KEY_REVOKED_IDS] = current - linkId
        }
    }

    override fun revokedLinkIds(): Flow<Set<String>> = dataStore.data
        .map { prefs -> prefs[KEY_REVOKED_IDS] ?: emptySet() }
        .distinctUntilChanged()

    companion object {
        private val KEY_REVOKED_IDS = stringSetPreferencesKey("revoked_link_ids")
    }
}

private val Context.revocationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "com.launcher.paired.revocation_v1",
)
