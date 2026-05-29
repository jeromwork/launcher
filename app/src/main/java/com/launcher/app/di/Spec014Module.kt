package com.launcher.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.launcher.adapters.edit.DataStoreNamedConfigsLocalStore
import com.launcher.api.edit.NamedConfigsLocalStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Spec 014 Koin wiring per Plan §6.
 *
 * **F-014.0 scope** (local-only):
 *  - One DataStore<Preferences> instance namespaced `com.launcher.f014.named_configs_v1`.
 *  - One [NamedConfigsLocalStore] binding → [DataStoreNamedConfigsLocalStore].
 *
 * Tests inject [com.launcher.api.edit.FakeNamedConfigsLocalStore] directly,
 * bypassing this module (commonTest fixture).
 *
 * F-014.1 will add parallel `RemoteNamedConfigsStore` binding (Firestore)
 * через отдельный adapter without changing this file's shape — port stays
 * the same; merge happens at use site через future MergedNamedConfigsRepository.
 *
 * TODO(server-roadmap, F-014.1): add RemoteNamedConfigsStore + MergedRepository.
 */
val spec014Module = module {

    // DataStore namespaced per contracts/named-config-local.md §Persistence.
    single<DataStore<Preferences>>(named(NAMED_CONFIGS_DATA_STORE)) {
        PreferenceDataStoreFactory.create(
            produceFile = {
                androidContext().preferencesDataStoreFile("com.launcher.f014.named_configs_v1")
            },
        )
    }

    single<NamedConfigsLocalStore> {
        DataStoreNamedConfigsLocalStore(
            dataStore = get(named(NAMED_CONFIGS_DATA_STORE)),
        )
    }
}

/** Qualifier name for the DataStore instance backing [NamedConfigsLocalStore]. */
const val NAMED_CONFIGS_DATA_STORE: String = "namedConfigsDataStore"
