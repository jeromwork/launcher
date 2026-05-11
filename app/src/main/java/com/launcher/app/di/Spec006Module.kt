package com.launcher.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.launcher.api.PresetRepository
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.alerts.AlertBannerStateProvider
import com.launcher.api.capability.CapabilityRepository
import com.launcher.api.capability.IconStorage
import com.launcher.api.health.HealthRepository
import com.launcher.api.settings.SettingsRepository
import com.launcher.core.airplane.AirplaneModeSource
import com.launcher.core.capability.AndroidCapabilityCollector
import com.launcher.core.capability.AndroidCapabilityRepository
import com.launcher.core.capability.BundledIconStorage
import com.launcher.core.capability.CapabilitySnapshotProjection
import com.launcher.core.diagnostics.RecoveryEventLogger
import com.launcher.core.health.AndroidHealthCollector
import com.launcher.core.health.AndroidHealthRepository
import com.launcher.core.health.HealthSnapshotProjection
import com.launcher.core.settings.AndroidSettingsRepository
import com.launcher.core.settings.SettingsProjection
import com.launcher.ui.di.PRESET_REPOSITORY_OVERRIDE_QUALIFIER
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Spec 006 Koin wiring per Plan §"Phase 7 — DI wiring".
 *
 * Provides:
 *  - [RecoveryEventLogger] (singleton, no PII per FR-052)
 *  - [IconStorage] → [BundledIconStorage] (single implementation спека 006)
 *  - 3 DataStore<Preferences> instances (separate files per FR-046):
 *    capability, health, settings — keys namespaced
 *    `com.launcher.<feature>.<key>_v1`.
 *  - 3 projection adapters (CapabilitySnapshotProjection, HealthSnapshotProjection,
 *    SettingsProjection) — DataStore JSON read/write.
 *  - 3 collectors (AndroidCapabilityCollector, AndroidHealthCollector,
 *    AirplaneModeSource) — system event subscriptions.
 *  - 3 repositories (AndroidCapabilityRepository, AndroidHealthRepository,
 *    AndroidSettingsRepository) — wire collectors + projections.
 *  - [AlertBannerStateProvider] — domain logic combining health + settings + airplane.
 *
 * All repositories scoped via [ProcessLifecycleOwner.lifecycleScope] — they
 * live as long as the process; observer/broadcast registrations don't leak.
 */
val spec006Module = module {

    // -- Diagnostics --
    single { RecoveryEventLogger() }

    // -- Icon storage --
    single<IconStorage> { BundledIconStorage(logger = get()) }

    // -- DataStore instances (one file per snapshot type, per FR-046) --
    single<DataStore<Preferences>>(named("capabilityDataStore")) {
        PreferenceDataStoreFactory.create(
            produceFile = {
                androidContext().preferencesDataStoreFile("com.launcher.capability.snapshot_v1")
            },
        )
    }
    single<DataStore<Preferences>>(named("healthDataStore")) {
        PreferenceDataStoreFactory.create(
            produceFile = {
                androidContext().preferencesDataStoreFile("com.launcher.health.snapshot_v1")
            },
        )
    }
    single<DataStore<Preferences>>(named("settingsDataStore")) {
        PreferenceDataStoreFactory.create(
            produceFile = {
                androidContext().preferencesDataStoreFile("com.launcher.settings.banners_v1")
            },
        )
    }

    // -- Projections --
    single {
        CapabilitySnapshotProjection(
            dataStore = get(named("capabilityDataStore")),
            logger = get(),
        )
    }
    single {
        HealthSnapshotProjection(
            dataStore = get(named("healthDataStore")),
            appVersion = appVersionName(androidContext()),
            logger = get(),
        )
    }
    single {
        SettingsProjection(
            dataStore = get(named("settingsDataStore")),
            logger = get(),
        )
    }

    // -- Collectors / sources --
    single {
        AndroidCapabilityCollector(
            context = androidContext(),
            providerRegistry = get<ProviderRegistry>(),
        )
    }
    single {
        AndroidHealthCollector(
            context = androidContext(),
            appVersion = appVersionName(androidContext()),
            scope = ProcessLifecycleOwner.get().lifecycleScope,
            logger = get(),
        )
    }
    single {
        AirplaneModeSource(
            context = androidContext(),
            logger = get(),
        )
    }

    // -- Repositories --
    single<CapabilityRepository> {
        AndroidCapabilityRepository(
            collector = get(),
            projection = get(),
            scope = ProcessLifecycleOwner.get().lifecycleScope,
        )
    }
    single<HealthRepository> {
        AndroidHealthRepository(
            collector = get(),
            projection = get(),
            scope = ProcessLifecycleOwner.get().lifecycleScope,
        )
    }
    single<SettingsRepository> {
        AndroidSettingsRepository(
            projection = get(),
            presetRepository = get<PresetRepository>(named(PRESET_REPOSITORY_OVERRIDE_QUALIFIER)),
            scope = ProcessLifecycleOwner.get().lifecycleScope,
        )
    }

    // -- Domain banner derivation --
    single {
        AlertBannerStateProvider(
            healthRepository = get(),
            settingsRepository = get(),
            airplaneMode = get<AirplaneModeSource>().flow,
        )
    }
}

/** Reads `BuildConfig.VERSION_NAME`-equivalent through PackageManager. */
private fun appVersionName(context: android.content.Context): String = try {
    @Suppress("DEPRECATION")
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName ?: "0.0.0"
} catch (_: Throwable) {
    "0.0.0"
}
