package com.launcher.app

import android.app.Application
import androidx.work.Configuration
import com.launcher.adapters.auth.installAuthActivityTracker
import com.launcher.adapters.lifecycle.ConfigRefreshWorker
import com.launcher.adapters.lifecycle.ConfigSyncWorkerFactory
import com.launcher.app.di.appAndroidModule
import com.launcher.app.di.assertNoFakeCryptoInRelease
import com.launcher.app.di.cryptoModule
import com.launcher.app.di.f016CryptoModule
import com.launcher.app.di.f018KeysBackendModule
import com.launcher.app.di.f018KeysModule
import com.launcher.app.di.pairingModule
import com.launcher.app.di.spec006Module
import com.launcher.app.di.spec014Module
import com.launcher.app.di.spec015Module
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
        // Spec 017 (F-4) — Credential Manager requires Activity context;
        // tracker заполняет ActivityHolder на каждый resume.
        installAuthActivityTracker()
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
                f016CryptoModule, // spec 016 (F-CRYPTO) ports → Libsodium adapters
                f018KeysModule,   // spec 018 (F-5) RootKeyManager + IdentityProof + Argon2id KDF
                f018KeysBackendModule, // spec 018 RecoveryKeyVault (flavor-resolved)
                setupModule,   // spec 010 GmsAvailabilityPort + List<SetupCheck>
                spec014Module, // spec 014 tile-editing — empty в Phase 0, bindings landed в T060
                spec015Module, // spec 015 (F-3) wizard + localization + senior UI
            )
        }
        // Spec 016 (F-CRYPTO) FR-018 / SC-011 — fail-fast if any Fake* crypto adapter
        // is wired in a non-debug build. Defense-in-depth alongside the Detekt rule
        // (compile-time) and R8 strip (-assumenosideeffects family.crypto.fake.**).
        if (!BuildConfig.DEBUG) {
            assertNoFakeCryptoInRelease { type -> org.koin.java.KoinJavaComponent.get(type) }
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
