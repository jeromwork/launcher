package com.launcher.ui.di

import com.launcher.api.ModuleDescriptor
import com.launcher.api.PresetRepository
import com.launcher.api.action.ActionDispatcher
import com.launcher.api.action.ProviderRegistry
import com.launcher.core.LauncherCore
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
    // Spec 010 T032a — FlowRepository is now bound by the flavor's backend module
    // (ConfigBackedFlowRepository over ConfigEditor + LinkRegistry); LauncherCore
    // takes it as a constructor parameter. The Koin graph passes `get()` here, so
    // a missing FlowRepository binding fails at startup with a clear DI error
    // rather than silently falling back to the deleted MockFlowRepository.
    single<LauncherCore> {
        LauncherCore(
            context = androidContext(),
            flowRepository = get(),
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
    single<EventRouter> { get<LauncherCore>().eventRouter }
    single<AppIndex> { get<LauncherCore>().appIndex }
    single<ActionDispatcher> { get<LauncherCore>().androidActionDispatcher }
    single<ProviderRegistry> { get<LauncherCore>().androidProviderRegistry }
    single<MockContactsRepository> { get<LauncherCore>().mockContactsRepository }
}
