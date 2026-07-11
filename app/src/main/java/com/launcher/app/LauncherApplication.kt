package com.launcher.app

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.Configuration
import com.launcher.adapters.auth.installAuthActivityTracker
import com.launcher.adapters.lifecycle.ConfigRefreshWorker
import com.launcher.adapters.lifecycle.ConfigSyncWorkerFactory
import com.launcher.app.push.FcmTokenBootstrapPublisher
import com.launcher.app.di.appAndroidModule
import com.launcher.app.di.assertNoFakeCryptoInRelease
import com.launcher.app.di.cloudModule
import com.launcher.app.di.cryptokitModule
import com.launcher.app.di.f018KeysBackendModule
import com.launcher.app.di.f018KeysModule
import com.launcher.app.di.f019PushBackendModule
import com.launcher.app.push.f019PushCommonModule
import com.launcher.app.di.pairingModule
import com.launcher.app.di.spec006Module
import com.launcher.app.di.spec014Module
import com.launcher.app.di.spec015Module
import com.launcher.app.di.task120Module
import com.launcher.app.di.task65Module
import com.launcher.api.wizard.UserPreferencesStore
import com.launcher.core.LauncherCore
import com.launcher.di.backendModule
import com.launcher.di.setupModule
import com.launcher.app.data.identity.OurJwtProvider
import com.launcher.ui.di.androidPlatformModule
import com.launcher.ui.di.coreCommonModule
import family.keys.api.AuthIdentity
import family.keys.api.BootstrapError
import family.keys.api.EnvelopeBootstrap
import family.keys.api.IdentityProof
import family.keys.api.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val identityProof: IdentityProof by inject()
    private val envelopeBootstrap: EnvelopeBootstrap by inject()
    private val fcmTokenBootstrapPublisher: FcmTokenBootstrapPublisher by inject()

    /**
     * Application-scoped supervisor that hosts the F-5b envelope bootstrap
     * observer. SupervisorJob so a transient bootstrap failure does not kill
     * the listener; subsequent identity changes still trigger fresh attempts.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // F-5b E2E (-PuseFirebaseEmulator=true): route Firebase SDK to the
        // local emulator instead of cloud `launcher-old-dev`. Must run BEFORE
        // any Firebase singleton is constructed by Koin or by the SDK itself.
        wireFirebaseEmulatorIfRequested()
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@LauncherApplication)
            // Debug overlay (spec 018 F-5b round-trip приёмка) — синхронный
            // AsyncConfigPushQueue вместо WorkManager. Класс существует только
            // в realBackendDebug source set; lookup через reflection, чтобы
            // main не зависел compile-time от debug-only классов.
            val debugOverlays = loadDebugOverlayModules()
            // backendModule is flavor-resolved: Firebase wiring under
            // realBackend, Fake wiring under mockBackend (spec 007 FR-034/FR-035).
            modules(
                coreCommonModule,
                androidPlatformModule,
                appAndroidModule,
                spec006Module,
                backendModule, // flavor-resolved (Firebase or Fakes)
                pairingModule, // spec 007 PairingService + PairingViewModel
                cryptokitModule, // TASK-51 unified: spec 016 (F-CRYPTO) ports + spec 011 pairing-side adapters + coordinator
                f018KeysModule,   // spec 018 (F-5) RootKeyManager + IdentityProof + Argon2id KDF
                f018KeysBackendModule, // spec 018 RecoveryKeyBackup (flavor-resolved)
                f019PushCommonModule,  // spec 019 (F-5c) PushHandlerRegistry + ConfigUpdatedHandler
                f019PushBackendModule, // spec 019 PushTrigger / FcmTokenPublisher / ConfigChangeNotifier (flavor-resolved)
                cloudModule,   // TASK-49 CloudAvailability + EmergencyNumberResolver + SOSDialerAlternative
                setupModule,   // spec 010 GmsAvailabilityPort + List<SetupCheck>
                spec014Module, // spec 014 tile-editing — empty в Phase 0, bindings landed в T060
                spec015Module, // spec 015 (F-3) wizard + localization + senior UI
                task65Module,  // TASK-65 PoolSource + ProfileSwitchStrategy + ProfileStore
                task120Module, // TASK-120 Preset composition foundation (com.launcher.preset.*)
            )
            if (debugOverlays.isNotEmpty()) {
                allowOverride(true)
                modules(debugOverlays)
            }
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

        // TASK-7 / FR-018 — restore persisted app-level locale BEFORE any UI
        // is inflated. AppCompatDelegate.setApplicationLocales is idempotent
        // and effectively no-op when the override equals system locale. We
        // accept the brief runBlocking here because (a) the read is a single
        // DataStore key (~ms), (b) it must complete before Activity create,
        // and (c) matches the existing project pattern (see core start()).
        applyPersistedLocaleOverride()

        // Spec 018 F-5b: publish this device's X25519 public key into the
        // PublicKeyDirectory under the signed-in user's namespace, so other
        // devices and grant holders can resolve us as a recipient for envelope
        // encryption. EnvelopeBootstrap.bootstrap() is idempotent (Firestore
        // set overwrites the same entry), so we re-run on every identity
        // transition without side-effects. teardown() is fired on sign-out
        // to remove the device from the directory.
        observeIdentityAndBootstrapEnvelope()
    }

    private fun applyPersistedLocaleOverride() {
        val store: UserPreferencesStore = org.koin.java.KoinJavaComponent.get(
            UserPreferencesStore::class.java,
        )
        val override = runBlocking { store.current().languageOverride } ?: return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(override))
    }

    private fun observeIdentityAndBootstrapEnvelope() {
        identityProof.identityFlow
            .distinctUntilChangedBy { it?.stableId }
            .onEach { identity ->
                if (identity != null) {
                    // task-6 Track B/C wiring 2026-06-30: bind stableId custom
                    // claim on the Firebase Auth user before any downstream
                    // bootstrap. Idempotent — the identity Worker short-circuits
                    // on existing binding without re-allocating UUID. Absent in
                    // mockBackend (Koin binding lives in F018KeysBackendModule
                    // realBackend variant only).
                    warmupOurJwt(identity)
                    bootstrapEnvelope(identity)
                } else {
                    teardownEnvelope()
                }
            }
            .launchIn(applicationScope)
    }

    /**
     * TASK-119 (2026-07-09): warm up [OurJwtProvider] cache on first identity
     * emission. Not strictly required for correctness — WorkerRecoveryKeyBackup
     * would trigger the same exchange call on its first fetchBlob — but paying
     * the ~200 ms round-trip up front removes a visible delay on the first
     * recovery probe. mockBackend flavor omits OurJwtProvider from DI; the
     * call is a no-op there.
     */
    private suspend fun warmupOurJwt(identity: AuthIdentity) {
        val koin = org.koin.java.KoinJavaComponent.getKoin()
        val provider = koin.getOrNull<OurJwtProvider>()
        if (provider == null) {
            Log.d(TAG_ENVELOPE, "our-JWT warmup skipped (no OurJwtProvider in DI — mockBackend?)")
            return
        }
        val token = provider.currentIdToken()
        if (token != null) {
            Log.i(TAG_ENVELOPE, "our-JWT warmup ok for identity=${identity.stableId}")
        } else {
            Log.w(TAG_ENVELOPE, "our-JWT warmup failed for identity=${identity.stableId} — will retry on first backup call")
        }
    }

    private fun bootstrapEnvelope(identity: AuthIdentity) {
        applicationScope.launch {
            when (val r = envelopeBootstrap.bootstrap()) {
                is Outcome.Success -> {
                    Log.i(
                        TAG_ENVELOPE,
                        "envelope bootstrap published for uid=${identity.stableId}"
                    )
                    // T131 (spec 019 FR-027): после успешного envelope bootstrap
                    // публикуем текущий FCM token, иначе он попадёт в Firestore
                    // только при ротации (onNewToken) — а первый раз никогда.
                    fcmTokenBootstrapPublisher.publishCurrent()
                }
                is Outcome.Failure -> Log.w(
                    TAG_ENVELOPE,
                    "envelope bootstrap failed for uid=${identity.stableId}: ${formatError(r.error)}"
                )
            }
        }
    }

    private fun teardownEnvelope() {
        applicationScope.launch {
            when (val r = envelopeBootstrap.teardown()) {
                is Outcome.Success -> Log.i(TAG_ENVELOPE, "envelope teardown completed")
                is Outcome.Failure -> Log.w(
                    TAG_ENVELOPE,
                    "envelope teardown failed: ${formatError(r.error)}"
                )
            }
        }
    }

    private fun formatError(error: BootstrapError): String = when (error) {
        BootstrapError.NoIdentity -> "no-identity"
        is BootstrapError.Backend -> "backend: ${error.message}"
    }

    /**
     * If this build was compiled with `-PuseFirebaseEmulator=true`, route the
     * Firebase Firestore + Auth SDKs to the local emulator (10.0.2.2:8080/9099).
     *
     * Implemented as reflection lookup of `realBackend`-flavor class
     * `FirebaseEmulatorWiring.apply()` so the main source set does not
     * compile-depend on Firebase types (mockBackend variant has no Firebase SDK).
     */
    /**
     * Reflection lookup of debug-only Koin overlay modules (currently
     * `DebugOverlayModules.modules()` в realBackendDebug source set). Returns
     * empty list on release / mockBackend builds where the marker class is
     * absent.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadDebugOverlayModules(): List<org.koin.core.module.Module> = try {
        val cls = Class.forName("com.launcher.app.debug.di.DebugOverlayModules")
        val result = cls.getMethod("modules").invoke(null) as? List<org.koin.core.module.Module>
        result ?: emptyList()
    } catch (e: ClassNotFoundException) {
        emptyList()
    } catch (t: Throwable) {
        Log.w(TAG_EMULATOR, "Debug overlay modules lookup failed: ${t.message}")
        emptyList()
    }

    private fun wireFirebaseEmulatorIfRequested() {
        if (!BuildConfig.USE_FIREBASE_EMULATOR) return
        try {
            val cls = Class.forName("com.launcher.app.firebase.FirebaseEmulatorWiring")
            cls.getMethod("apply").invoke(null)
        } catch (e: ClassNotFoundException) {
            Log.w(
                TAG_EMULATOR,
                "USE_FIREBASE_EMULATOR=true but FirebaseEmulatorWiring class not found " +
                    "(this is mockBackend variant or class was stripped) — Firebase calls will fail."
            )
        } catch (t: Throwable) {
            Log.e(TAG_EMULATOR, "Failed to wire Firebase emulator", t)
        }
    }

    override fun onTerminate() {
        core.stop()
        super.onTerminate()
    }

    private companion object {
        const val TAG_ENVELOPE: String = "F5bEnvelopeBootstrap"
        const val TAG_EMULATOR: String = "FirebaseEmulatorWiring"
    }
}
