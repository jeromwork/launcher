package com.launcher.app.preset

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.presetDataStore by preferencesDataStore(name = "launcher_preset")

class DataStorePresetRepository(context: Context) : PresetRepository {
    private val store = context.applicationContext.presetDataStore
    private val key = stringPreferencesKey("active_preset_slug")

    override suspend fun getActivePreset(): FlowPreset? =
        FlowPreset.fromSlug(store.data.map { it[key] }.first())

    override suspend fun setActivePreset(preset: FlowPreset) {
        store.edit { it[key] = preset.slug }
    }

    override suspend fun clear() {
        store.edit { it.remove(key) }
    }

    override fun observeActivePreset(): Flow<FlowPreset?> =
        store.data.map { FlowPreset.fromSlug(it[key]) }
}
