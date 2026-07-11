package com.launcher.app.preset.task120.adapter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.port.CapabilityQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.capabilityStore by preferencesDataStore(name = "task120_capabilities")

/**
 * DataStore-backed CapabilityQuery adapter. Evidence values are hashed / opaque
 * on the client side per rule 13; this adapter only stores the flag names in a
 * String set. Evidence blobs live in a separate secure store (Keystore) —
 * not persisted here for MVP.
 */
class DataStoreCapabilityAdapter(context: Context) : CapabilityQuery {
    private val store = context.applicationContext.capabilityStore
    private val activeKey = stringSetPreferencesKey("active_flags")

    override suspend fun isActive(flag: CapabilityFlag): Boolean =
        store.data.map { it[activeKey] ?: emptySet() }.first().contains(nameOf(flag))

    override suspend fun markActive(flag: CapabilityFlag, evidence: CapabilityQuery.Evidence) {
        store.edit { prefs ->
            val current = prefs[activeKey] ?: emptySet()
            prefs[activeKey] = current + nameOf(flag)
        }
    }

    override suspend fun markInactive(flag: CapabilityFlag) {
        store.edit { prefs ->
            val current = prefs[activeKey] ?: emptySet()
            prefs[activeKey] = current - nameOf(flag)
        }
    }

    private fun nameOf(flag: CapabilityFlag): String = flag::class.simpleName ?: flag.toString()
}
