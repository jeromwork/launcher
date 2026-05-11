package com.launcher.core.capability

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.launcher.api.capability.Capability
import com.launcher.api.wireformat.WireFormatJson
import com.launcher.core.diagnostics.RecoveryEventLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

/**
 * DataStore projection of the [Capability] snapshot per FR-005, FR-046.
 *
 * Storage key: `com.launcher.capability.snapshot_v1` (per FR-046 namespace
 * convention; `_v1` suffix anticipates schema-bumps that may want fresh keys).
 *
 * Read path: deserialise JSON → `List<Capability>`. On corruption (parse fail
 * / missing key), returns `emptyList()` and logs a `corruption` event (FR-051
 * parallel — Capability snapshot recovery is "show empty until next rebuild").
 *
 * Write path: serialise to JSON, write to DataStore. Idempotent — overwrites.
 *
 * Stays out of [com.launcher.api.capability.CapabilityRepository] interface
 * scope: this is implementation detail of [AndroidCapabilityRepository], not
 * a domain port.
 */
class CapabilitySnapshotProjection(
    private val dataStore: DataStore<Preferences>,
    private val logger: RecoveryEventLogger? = null,
) {
    private val json = WireFormatJson.json
    private val serializer = ListSerializer(Capability.serializer())

    val flow: Flow<List<Capability>> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY] ?: return@map emptyList<Capability>()
            try {
                json.decodeFromString(serializer, raw)
            } catch (t: Throwable) {
                logger?.log(
                    RecoveryEventLogger.Category.Corruption,
                    "capability_snapshot_parse_fail",
                    mapOf("err" to (t.message ?: "unknown").take(40)),
                )
                emptyList()
            }
        }
        .catch { t ->
            logger?.log(
                RecoveryEventLogger.Category.Corruption,
                "capability_datastore_read_fail",
                mapOf("err" to (t.message ?: "unknown").take(40)),
            )
            emit(emptyList())
        }

    suspend fun write(snapshot: List<Capability>) {
        val raw = json.encodeToString(serializer, snapshot)
        dataStore.edit { prefs -> prefs[KEY] = raw }
    }

    companion object {
        val KEY = stringPreferencesKey("com.launcher.capability.snapshot_v1")
    }
}
