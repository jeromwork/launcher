package com.launcher.adapters.edit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.launcher.api.edit.NamedConfig
import com.launcher.api.edit.NamedConfigWireFormat
import com.launcher.api.edit.NamedConfigsLocalStore
import com.launcher.api.edit.StoreError
import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Android DataStore adapter for [NamedConfigsLocalStore] (T056).
 *
 * Storage key: `com.launcher.f014.named_configs_v1` per
 * contracts/named-config-local.md §Persistence specifics.
 *
 * Serialization: [NamedConfigWireFormat] envelope JSON, fail-closed
 * forward-compat (CHK008 wire-format).
 *
 * Atomicity: all writes use `DataStore.edit { }` block — Android DataStore
 * guarantees per-key atomicity (no torn writes between concurrent edits).
 *
 * TODO(server-roadmap): F-014.1 will pair this с RemoteNamedConfigsStore
 * (Firestore), merged via MergedNamedConfigsRepository. This adapter stays
 * as the local cache.
 *
 * Note: NFC normalization of `configName` is caller's responsibility via
 * [com.launcher.api.edit.ConfigNameValidator] before passing to [create].
 */
class DataStoreNamedConfigsLocalStore(
    private val dataStore: DataStore<Preferences>,
) : NamedConfigsLocalStore {

    override val configs: Flow<List<NamedConfig>> = dataStore.data
        .map { prefs -> readEnvelope(prefs).getOrElse(emptyList()) }
        .catch { emit(emptyList()) }

    override suspend fun create(config: NamedConfig): Outcome<Unit, StoreError> =
        editConfigs { current ->
            if (current.size >= NamedConfig.MAX_CONFIGS_PER_ADMIN) {
                return@editConfigs Outcome.Failure(StoreError.LimitReached)
            }
            val normalized = config.configName.lowercase()
            if (current.any { it.configName.lowercase() == normalized }) {
                return@editConfigs Outcome.Failure(
                    StoreError.NameAlreadyExists(config.configName),
                )
            }
            val cleared = if (config.isDefault) {
                current.map { if (it.isDefault) it.copy(isDefault = false) else it }
            } else {
                current
            }
            Outcome.Success(cleared + config)
        }

    override suspend fun update(
        configName: String,
        transform: (NamedConfig) -> NamedConfig,
    ): Outcome<Unit, StoreError> = editConfigs { current ->
        val index = current.indexOfFirst { it.configName == configName }
        if (index < 0) return@editConfigs Outcome.Failure(StoreError.NotFound)
        val transformed = transform(current[index])
        // FR-003a: refuse if this clears the only default.
        if (current[index].isDefault && !transformed.isDefault) {
            val anyOtherDefault = current.withIndex()
                .any { (i, c) -> i != index && c.isDefault }
            if (!anyOtherDefault) {
                return@editConfigs Outcome.Failure(StoreError.DefaultMustExist)
            }
        }
        val updated = current.toMutableList()
        updated[index] = transformed
        if (transformed.isDefault && !current[index].isDefault) {
            for (i in updated.indices) {
                if (i != index && updated[i].isDefault) {
                    updated[i] = updated[i].copy(isDefault = false)
                }
            }
        }
        Outcome.Success(updated.toList())
    }

    override suspend fun markDefault(configName: String): Outcome<Unit, StoreError> =
        editConfigs { current ->
            if (current.none { it.configName == configName }) {
                return@editConfigs Outcome.Failure(StoreError.NotFound)
            }
            val updated = current.map { c ->
                c.copy(isDefault = c.configName == configName)
            }
            Outcome.Success(updated)
        }

    override suspend fun applyToCurrentDevice(
        configName: String,
        thisDeviceId: String,
    ): Outcome<Unit, StoreError> = editConfigs { current ->
        val index = current.indexOfFirst { it.configName == configName }
        if (index < 0) return@editConfigs Outcome.Failure(StoreError.NotFound)
        val updated = current.toMutableList()
        updated[index] = current[index].copy(
            activeDeviceIds = current[index].activeDeviceIds + thisDeviceId,
            orphanedAt = null,
        )
        Outcome.Success(updated.toList())
    }

    override suspend fun removeFromCurrentDevice(
        configName: String,
        thisDeviceId: String,
        nowMillis: Long,
    ): Outcome<Unit, StoreError> = editConfigs { current ->
        val index = current.indexOfFirst { it.configName == configName }
        if (index < 0) return@editConfigs Outcome.Failure(StoreError.NotFound)
        val target = current[index]
        val newActiveSet = target.activeDeviceIds - thisDeviceId
        val willBecomeOrphan = newActiveSet.isEmpty() && target.activeDeviceIds.isNotEmpty()
        if (target.isDefault && willBecomeOrphan) {
            val anyOtherDefault = current.withIndex()
                .any { (i, c) -> i != index && c.isDefault && c.isActive }
            if (!anyOtherDefault) {
                return@editConfigs Outcome.Failure(StoreError.DefaultMustExist)
            }
        }
        val updated = current.toMutableList()
        updated[index] = target.copy(
            activeDeviceIds = newActiveSet,
            orphanedAt = if (willBecomeOrphan) nowMillis else target.orphanedAt,
        )
        Outcome.Success(updated.toList())
    }

    // ─── Internal helpers ─────────────────────────────────────────────────

    private suspend inline fun editConfigs(
        block: (current: List<NamedConfig>) -> Outcome<List<NamedConfig>, StoreError>,
    ): Outcome<Unit, StoreError> {
        // Read current state first (peek + decode).
        val current = readEnvelopeBlocking()
        val result = when (current) {
            is Outcome.Failure -> return current
            is Outcome.Success -> block(current.value)
        }
        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                val envelope = NamedConfigWireFormat.Envelope(configs = result.value)
                val raw = NamedConfigWireFormat.serialize(envelope)
                dataStore.edit { prefs -> prefs[KEY] = raw }
                Outcome.Success(Unit)
            }
        }
    }

    private suspend fun readEnvelopeBlocking(): Outcome<List<NamedConfig>, StoreError> {
        val prefs = dataStore.data.first()
        return readEnvelope(prefs)
    }

    private fun readEnvelope(prefs: Preferences): Outcome<List<NamedConfig>, StoreError> {
        val raw = prefs[KEY] ?: return Outcome.Success(emptyList())
        return when (val parsed = NamedConfigWireFormat.deserialize(raw)) {
            is Outcome.Success -> Outcome.Success(parsed.value.configs)
            is Outcome.Failure -> Outcome.Failure(parsed.error)
        }
    }

    private fun <T, E> Outcome<T, E>.getOrElse(default: T): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> default
    }

    companion object {
        val KEY = stringPreferencesKey("com.launcher.f014.named_configs_v1")
    }
}
