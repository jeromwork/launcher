package com.launcher.app.di

import com.launcher.adapters.auth.ActivityHolder
import com.launcher.app.preset.task120.adapter.AndroidLocalizedResources
import com.launcher.app.preset.task120.adapter.BundledPoolSource
import com.launcher.app.preset.task120.adapter.BundledPresetSource
import com.launcher.app.preset.task120.adapter.DataStoreCapabilityAdapter
import com.launcher.app.preset.task120.adapter.DataStoreProfileStore
import com.launcher.app.preset.task120.adapter.NoopPairingService
import com.launcher.app.preset.task120.catalog.ThemeCatalog
import com.launcher.app.preset.task120.facade.AndroidPackageManagerFacade
import com.launcher.app.preset.task120.facade.AndroidStoreIntentFacade
import com.launcher.app.preset.task120.facade.AppThemeController
import com.launcher.app.preset.task120.facade.DataStoreAppThemeController
import com.launcher.app.preset.task120.facade.DataStoreUiPrefsFacade
import com.launcher.app.preset.task120.facade.HomeScreenFacade
import com.launcher.app.preset.task120.facade.InMemoryHomeScreenFacade
import com.launcher.app.preset.task120.facade.PackageManagerFacade
import com.launcher.app.preset.task120.facade.StoreIntentFacade
import com.launcher.app.preset.task120.facade.UiPrefsFacade
import com.launcher.app.preset.task120.provider.AppTileProvider
import com.launcher.app.preset.task120.provider.FontSizeProvider
import com.launcher.app.preset.task120.provider.LanguageProvider
import com.launcher.app.preset.task120.provider.LauncherRoleProvider
import com.launcher.app.preset.task120.provider.SosProvider
import com.launcher.app.preset.task120.provider.StatusBarPolicyProvider
import com.launcher.app.preset.task120.provider.ThemeProvider
import com.launcher.app.preset.task120.provider.ToolbarProvider
import com.launcher.app.preset.task126.BundledHintPoolSource
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
import com.launcher.preset.port.HintPoolSource
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
val presetModule = module {

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

    // TASK-126 Phase 1.6/1.8 — theme facade + catalog + hint pool source.
    single<AppThemeController> { DataStoreAppThemeController(androidContext()) }
    single { ThemeCatalog(androidContext()) }
    single<HintPoolSource> { BundledHintPoolSource(androidContext().assets) }

    // Providers
    single { AppTileProvider(pm = get(), home = get(), store = get()) }
    single { FontSizeProvider(ui = get()) }
    single { SosProvider(home = get(), pairing = get()) }
    single { ToolbarProvider(home = get()) }

    // TASK-126 Phase 1.6/1.8 — new providers (T031-T034).
    // ActivityHolder is populated by installAuthActivityTracker() in
    // LauncherApplication.onCreate() and provides the current foreground Activity
    // for role-request dialogs (LauncherRole) and window-flag mutations
    // (StatusBarPolicy). WeakReference-based, safe to hold at singleton scope.
    single {
        LauncherRoleProvider(
            context = androidContext(),
            currentActivity = { ActivityHolder.current() },
        )
    }
    single { ThemeProvider(controller = get()) }
    single { LanguageProvider() }
    single { StatusBarPolicyProvider(currentActivity = { ActivityHolder.current() }) }

    // ProviderRegistry — map-backed, matches contracts/provider-port.md fallback semantics
    single<ProviderRegistry> {
        val handlers: Map<HandlerKey, Provider<out Component>> = mapOf(
            HandlerKey(Component.AppTile::class) to get<AppTileProvider>(),
            HandlerKey(Component.FontSize::class) to get<FontSizeProvider>(),
            HandlerKey(Component.Sos::class) to get<SosProvider>(),
            HandlerKey(Component.Toolbar::class) to get<ToolbarProvider>(),
            // TASK-126 Phase 1.8 additions (FR-001).
            HandlerKey(Component.LauncherRole::class) to get<LauncherRoleProvider>(),
            HandlerKey(Component.Theme::class) to get<ThemeProvider>(),
            HandlerKey(Component.Language::class) to get<LanguageProvider>(),
            HandlerKey(Component.StatusBarPolicy::class) to get<StatusBarPolicyProvider>(),
        )
        DefaultProviderRegistry(handlers, runtimePlatform = "Android", runtimeVendor = null)
    }

    // CapabilityContract — MVP: empty sets for all subtypes (TASK-120 + TASK-126).
    // draft-1 introduces SignInGoogle with provides = {CloudSession}.
    // TASK-126 new subtypes (LauncherRole/Theme/Language/StatusBarPolicy) require
    // no capability flags and provide none in MVP — they are pure device-local
    // Android surfaces.
    single<CapabilityContract> {
        object : CapabilityContract {
            private val requires: Map<KClass<out Component>, Set<CapabilityFlag>> = mapOf(
                Component.AppTile::class to emptySet(),
                Component.FontSize::class to emptySet(),
                Component.Sos::class to emptySet(),
                Component.Toolbar::class to emptySet(),
                Component.LauncherRole::class to emptySet(),
                Component.Theme::class to emptySet(),
                Component.Language::class to emptySet(),
                Component.StatusBarPolicy::class to emptySet(),
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
            // TASK-127 AC #8: without this the picker's choice was ignored and
            // bootstrap always built the simple-launcher profile.
            presetRepository = get(),
        )
    }

    // TASK-126 Phase 2 T051 — WizardViewModel bridges ReconcileEngine to Compose UI.
    single {
        com.launcher.app.wizard.WizardViewModel(
            bootstrap = get(),
            engine = get(),
            store = get(),
        )
    }

    // TASK-126 Phase 2 T056 — post-wizard kiosk apply (StatusBar + LauncherRole).
    single {
        com.launcher.app.wizard.PostWizardKioskApply(
            registry = get(),
            store = get(),
        )
    }
}
