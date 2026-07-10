package com.launcher.app.di

import com.launcher.app.preset.task120.adapter.AndroidLocalizedResources
import com.launcher.app.preset.task120.adapter.BundledPoolSource
import com.launcher.app.preset.task120.adapter.BundledPresetSource
import com.launcher.app.preset.task120.adapter.DataStoreCapabilityAdapter
import com.launcher.app.preset.task120.adapter.DataStoreProfileStore
import com.launcher.app.preset.task120.adapter.NoopPairingService
import com.launcher.app.preset.task120.facade.AndroidPackageManagerFacade
import com.launcher.app.preset.task120.facade.AndroidStoreIntentFacade
import com.launcher.app.preset.task120.facade.DataStoreUiPrefsFacade
import com.launcher.app.preset.task120.facade.HomeScreenFacade
import com.launcher.app.preset.task120.facade.InMemoryHomeScreenFacade
import com.launcher.app.preset.task120.facade.PackageManagerFacade
import com.launcher.app.preset.task120.facade.StoreIntentFacade
import com.launcher.app.preset.task120.facade.UiPrefsFacade
import com.launcher.app.preset.task120.provider.AppTileProvider
import com.launcher.app.preset.task120.provider.FontSizeProvider
import com.launcher.app.preset.task120.provider.SosProvider
import com.launcher.app.preset.task120.provider.ToolbarProvider
import com.launcher.preset.engine.PresetDiff
import com.launcher.preset.engine.PresetValidator
import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.port.CapabilityContract
import com.launcher.preset.port.CapabilityQuery
import com.launcher.preset.port.ConditionEvaluator
import com.launcher.preset.port.DefaultProviderRegistry
import com.launcher.preset.port.LocalizedResources
import com.launcher.preset.port.PairingService
import com.launcher.preset.port.PoolSource
import com.launcher.preset.port.PresetSource
import com.launcher.preset.port.ProfileStateConditionEvaluator
import com.launcher.preset.port.ProfileStore
import com.launcher.preset.port.Provider
import com.launcher.preset.port.ProviderRegistry
import kotlin.reflect.KClass
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * TASK-120 Koin wiring. Composes ports → adapters → engine.
 *
 * Providers keyed via a plain Kotlin `Map<HandlerKey, Provider<*>>` —
 * equivalent to Hilt's `@IntoMap` pattern with `@ComponentKey`, but Koin-native.
 * Adding a new Component subtype = add its Provider binding here + update
 * CapabilityContract map (both MVP-empty for now).
 */
val task120Module = module {

    // Facades (ACL over Android SDK)
    single<PackageManagerFacade> { AndroidPackageManagerFacade(androidContext()) }
    single<HomeScreenFacade> { InMemoryHomeScreenFacade() }
    single<StoreIntentFacade> { AndroidStoreIntentFacade(androidContext()) }
    single<UiPrefsFacade> { DataStoreUiPrefsFacade(androidContext()) }

    // Persistence + capability + localization + pairing adapters
    single<PoolSource> { BundledPoolSource(androidContext()) }
    single<PresetSource> { BundledPresetSource(androidContext()) }
    single<ProfileStore> { DataStoreProfileStore(androidContext()) }
    single<CapabilityQuery> { DataStoreCapabilityAdapter(androidContext()) }
    single<LocalizedResources> { AndroidLocalizedResources(androidContext()) }
    single<PairingService> { NoopPairingService() }
    single<ConditionEvaluator> { ProfileStateConditionEvaluator() }

    // Providers
    single { AppTileProvider(pm = get(), home = get(), store = get()) }
    single { FontSizeProvider(ui = get()) }
    single { SosProvider(home = get(), pairing = get()) }
    single { ToolbarProvider(home = get()) }

    // ProviderRegistry — map-backed, matches contracts/provider-port.md fallback semantics
    single<ProviderRegistry> {
        val handlers: Map<HandlerKey, Provider<out Component>> = mapOf(
            HandlerKey(Component.AppTile::class) to get<AppTileProvider>(),
            HandlerKey(Component.FontSize::class) to get<FontSizeProvider>(),
            HandlerKey(Component.Sos::class) to get<SosProvider>(),
            HandlerKey(Component.Toolbar::class) to get<ToolbarProvider>(),
        )
        DefaultProviderRegistry(handlers, runtimePlatform = "Android", runtimeVendor = null)
    }

    // CapabilityContract — MVP: empty sets for all 4 subtypes.
    // draft-1 introduces SignInGoogle with provides = {CloudSession}.
    single<CapabilityContract> {
        object : CapabilityContract {
            private val requires: Map<KClass<out Component>, Set<CapabilityFlag>> = mapOf(
                Component.AppTile::class to emptySet(),
                Component.FontSize::class to emptySet(),
                Component.Sos::class to emptySet(),
                Component.Toolbar::class to emptySet(),
            )
            private val provides: Map<KClass<out Component>, Set<CapabilityFlag>> = requires

            override fun requires(componentType: KClass<out Component>): Set<CapabilityFlag> =
                requires[componentType] ?: emptySet()

            override fun provides(componentType: KClass<out Component>): Set<CapabilityFlag> =
                provides[componentType] ?: emptySet()
        }
    }

    // Engine
    single { ProfileFactory() }
    single { PresetValidator(contract = get()) }
    single { PresetDiff() }
    single { ReconcileEngine(registry = get(), store = get()) }

    // Bootstrap — loads bundled preset on first launch (T066).
    single {
        com.launcher.app.preset.task120.PresetBootstrap(
            poolSource = get(),
            presetSource = get(),
            validator = get(),
            factory = get(),
            store = get(),
        )
    }
}
