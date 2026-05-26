package com.launcher.app

import android.app.Application
import androidx.work.Configuration
import com.launcher.adapters.lifecycle.ConfigRefreshWorker
import com.launcher.adapters.lifecycle.ConfigSyncWorkerFactory
import com.launcher.app.di.appAndroidModule
import com.launcher.app.di.cryptoModule
import com.launcher.app.di.spec012Module
import com.launcher.app.di.pairingModule
import com.launcher.app.di.spec006Module
import com.launcher.core.LauncherCore
import com.launcher.di.backendModule
import com.launcher.di.setupModule
import com.launcher.ui.di.androidPlatformModule
import com.launcher.ui.di.coreCommonModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Process entry: starts Koin and the launcher core lifecycle.
 *
 * Per ADR-005 Amendment 2026-05-07a, dependency wiring goes through Koin
 * (KMP-compatible service locator) instead of manual constructor wiring in
 * Application.onCreate.
 */
class LauncherApplication : Application(), Configuration.Provider {

    private val core: LauncherCore by inject()
    private val workerFactory: ConfigSyncWorkerFactory by inject()

    /**
     * Configuration.Provider override (spec 008 FR-022 T3) — uses our custom
     * [ConfigSyncWorkerFactory] so WorkManager can construct DI-injected
     * [ConfigRefreshWorker].
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@LauncherApplication)
            // backendModule is flavor-resolved: Firebase wiring under
            // realBackend, Fake wiring under mockBackend (spec 007 FR-034/FR-035).
            modules(
                coreCommonModule,
                androidPlatformModule,
                appAndroidModule,
                spec006Module,
                backendModule, // flavor-resolved (Firebase or Fakes)
                pairingModule, // spec 007 PairingService + PairingViewModel
                cryptoModule,  // spec 011 crypto adapters + PairingCryptoCoordinator
                spec012Module, // spec 012 private-media facades + adapters
                setupModule,   // spec 010 GmsAvailabilityPort + List<SetupCheck>
            )
        }
        core.start()

        // Spec 008 FR-022 T3: schedule periodic config-refresh WorkManager job.
        // Idempotent via ExistingPeriodicWorkPolicy.KEEP — safe to call on every
        // Application.onCreate. Worker fires every 15 minutes when network is
        // available, fetching /config/current and applying if newer.
        ConfigRefreshWorker.schedulePeriodicRefresh(this)
    }

    override fun onTerminate() {
        core.stop()
        super.onTerminate()
    }
}
