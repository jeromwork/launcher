package com.launcher.cloud.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.launcher.api.auth.AuthProvider
import com.launcher.cloud.api.CloudAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Android implementation: DataStore-backed boolean, подписан на
 * [AuthProvider.currentUser]. Это единственный observer auth state'а в
 * системе (FR-003).
 *
 * Ключ namespace'нут (`cloud.availability.is_available`) — wire-format-ready
 * per checklist CHK013 (если когда-то понадобится экспортировать состояние,
 * имя поля уже стабильное).
 */
class CloudAvailabilityImpl(
    private val dataStore: DataStore<Preferences>,
    authProvider: AuthProvider,
    scope: CoroutineScope,
) : CloudAvailability {

    init {
        scope.launch {
            authProvider.currentUser.collect { identity ->
                val available = identity != null
                dataStore.edit { prefs -> prefs[KEY] = available }
            }
        }
    }

    override suspend fun isCloudAvailable(): Boolean =
        dataStore.data.first()[KEY] ?: false

    override val isCloudAvailableFlow: Flow<Boolean> =
        dataStore.data
            .map { it[KEY] ?: false }
            .distinctUntilChanged()

    companion object {
        internal val KEY = booleanPreferencesKey("cloud.availability.is_available")
    }
}
