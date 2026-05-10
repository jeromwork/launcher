package com.launcher.core.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.launcher.api.health.Connectivity
import com.launcher.api.health.Health
import com.launcher.api.wireformat.WireFormatJson
import com.launcher.core.diagnostics.RecoveryEventLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * DataStore projection of the [Health] snapshot per FR-005, FR-046.
 *
 * Storage key: `com.launcher.health.snapshot_v1`. On corruption: emits
 * "unknown / safe" default ([Health] with everything zeroed except
 * [Connectivity.None] и passed-in `appVersion`) — better than crash;
 * collector replaces it within milliseconds anyway from sticky broadcasts.
 */
class HealthSnapshotProjection(
    private val dataStore: DataStore<Preferences>,
    private val appVersion: String,
    private val logger: RecoveryEventLogger? = null,
) {
    private val json = WireFormatJson.json

    val flow: Flow<Health?> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY] ?: return@map null
            try {
                json.decodeFromString(Health.serializer(), raw)
            } catch (t: Throwable) {
                logger?.log(
                    RecoveryEventLogger.Category.Corruption,
                    "health_snapshot_parse_fail",
                    mapOf("err" to (t.message ?: "unknown").take(40)),
                )
                null
            }
        }
        .catch { t ->
            logger?.log(
                RecoveryEventLogger.Category.Corruption,
                "health_datastore_read_fail",
                mapOf("err" to (t.message ?: "unknown").take(40)),
            )
            emit(null)
        }

    suspend fun write(snapshot: Health) {
        val raw = json.encodeToString(Health.serializer(), snapshot)
        dataStore.edit { prefs -> prefs[KEY] = raw }
    }

    /** Cold-start "before any data" placeholder. */
    fun unknownDefault(): Health = Health(
        batteryPercent = 0,
        charging = false,
        connectivity = Connectivity.None,
        ringerVolumePercent = 0,
        audioStreamMuted = false,
        lastSeen = 0L,
        appVersion = appVersion,
    )

    companion object {
        val KEY = stringPreferencesKey("com.launcher.health.snapshot_v1")
    }
}
