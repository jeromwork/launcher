package com.launcher.app.di

import com.launcher.api.PresetRepository
import com.launcher.app.AppModuleDescriptors
import com.launcher.app.preset.DataStorePresetRepository
import com.launcher.ui.di.MODULE_DESCRIPTORS_QUALIFIER
import com.launcher.ui.di.PRESET_REPOSITORY_OVERRIDE_QUALIFIER
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * `:app`-only Koin registrations:
 *
 * - the static list of [com.launcher.api.ModuleDescriptor]s for [com.launcher.core.LauncherCore],
 * - the DataStore-backed override for [PresetRepository] so the active preset persists
 *   across process restarts (until [DataStorePresetRepository] migrates to multiplatform-settings
 *   in `:core/commonMain` per ADR-005 §6).
 */
val appAndroidModule = module {
    single(named(MODULE_DESCRIPTORS_QUALIFIER)) { AppModuleDescriptors.all }
    single<PresetRepository>(named(PRESET_REPOSITORY_OVERRIDE_QUALIFIER)) {
        DataStorePresetRepository(androidContext())
    }
}
