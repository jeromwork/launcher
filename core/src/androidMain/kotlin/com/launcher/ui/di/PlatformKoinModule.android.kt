package com.launcher.ui.di

import com.launcher.api.FlowRepository
import com.launcher.api.ModuleDescriptor
import com.launcher.api.PresetRepository
import com.launcher.api.action.ProviderRegistry
import com.launcher.core.LauncherCore
import com.launcher.core.actions.ActionDispatcher
import com.launcher.core.catalog.AppIndex
import com.launcher.core.contacts.MockContactsRepository
import com.launcher.core.events.EventRouter
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Qualifier for the list of [ModuleDescriptor]s the consumer (`:app`) supplies.
 * `:app` registers `single(named(MODULE_DESCRIPTORS_QUALIFIER)) { AppModuleDescriptors.all }`.
 */
const val MODULE_DESCRIPTORS_QUALIFIER = "com.launcher.core.moduleDescriptors"

/**
 * Optional override for [PresetRepository] supplied by `:app` (e.g. DataStore-backed).
 * If absent, [LauncherCore] falls back to its in-memory default.
 */
const val PRESET_REPOSITORY_OVERRIDE_QUALIFIER = "com.launcher.core.presetRepositoryOverride"

val androidPlatformModule = module {
    single<LauncherCore> {
        LauncherCore(
            context = androidContext(),
            moduleDescriptors = getOrNull<List<ModuleDescriptor>>(named(MODULE_DESCRIPTORS_QUALIFIER))
                ?: emptyList(),
            presetRepository = getOrNull<PresetRepository>(named(PRESET_REPOSITORY_OVERRIDE_QUALIFIER)),
        )
    }
    // PresetRepository: prefer the app-supplied override (DataStore / multiplatform-settings)
    // over the in-memory default exposed by LauncherCore — otherwise the picker writes to a
    // store that LauncherCore never reads, and active preset never persists across restarts.
    single<PresetRepository> {
        getOrNull<PresetRepository>(named(PRESET_REPOSITORY_OVERRIDE_QUALIFIER))
            ?: get<LauncherCore>().presetRepository
    }
    single<FlowRepository> { get<LauncherCore>().flowRepository }
    single<EventRouter> { get<LauncherCore>().eventRouter }
    single<AppIndex> { get<LauncherCore>().appIndex }
    single<ActionDispatcher> { get<LauncherCore>().actionDispatcher }

    // Spec 005: new dispatcher pipeline. Registered alongside the legacy
    // ActionDispatcher above. UI is migrated in Phase 5; the legacy class is
    // deleted in Phase 6. Both bindings point at single instances owned by
    // LauncherCore — no double construction.
    single<com.launcher.api.action.ActionDispatcher> { get<LauncherCore>().androidActionDispatcher }
    single<ProviderRegistry> { get<LauncherCore>().androidProviderRegistry }
    single<MockContactsRepository> { get<LauncherCore>().mockContactsRepository }
}
