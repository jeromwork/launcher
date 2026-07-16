package com.launcher.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.launcher.adapters.crypto.FirestoreDeviceIdentityRepository
import com.launcher.adapters.crypto.WorkerEncryptedMediaStorage
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.pairing.api.DeviceIdentityRepository
import cryptokit.pairing.api.EncryptedMediaStorage
import com.launcher.adapters.apps.InstalledAppsCatalogAdapter
import com.launcher.adapters.apps.OpenAppDispatcherAdapter
import com.launcher.adapters.config.AndroidSqlDriverProvider
import com.launcher.adapters.flow.ProfileBackedFlowRepository
import com.launcher.adapters.config.DefaultConfigEditor
import com.launcher.adapters.config.FirebaseConfigApplier
import com.launcher.adapters.config.SqlDelightLocalConfigStore
import com.launcher.adapters.contacts.SystemContactPickerAdapter
import com.launcher.adapters.contacts.VCardImporterAdapter
import com.launcher.adapters.history.FirestoreConfigHistoryAdapter
import com.launcher.adapters.identity.DataStoreDeviceIdProvider
import com.launcher.adapters.identity.FirebaseIdentityProvider
import com.launcher.adapters.lifecycle.ConfigSyncWorkerFactory
import com.launcher.adapters.lifecycle.ConnectivityManagerNetworkAvailability
import com.launcher.adapters.lifecycle.ProcessLifecycleForegroundEvents
import com.launcher.adapters.link.FirestoreLinkRegistry
import com.launcher.adapters.link.FirestoreManagedDevicesRegistry
import com.launcher.adapters.paired.DataStoreLocalLinkRevocationStore
import com.launcher.adapters.push.FcmRegistration
import com.launcher.adapters.push.FirebaseTokenSupplier
import com.launcher.adapters.push.LauncherPushReceiver
import com.launcher.adapters.push.WorkerPushSender
import com.launcher.adapters.sync.FirebaseRemoteSyncBackend
import com.launcher.adapters.config.db.ConfigStore
import com.launcher.api.FlowRepository
import com.launcher.api.apps.InstalledAppsCatalog
import com.launcher.api.apps.OpenAppDispatcher
import com.launcher.api.config.ConfigApplier
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.contacts.SystemContactPicker
import com.launcher.api.contacts.VCardImporter
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.lifecycle.AppForegroundEvents
import com.launcher.api.lifecycle.NetworkAvailability
import com.launcher.api.link.LinkRegistry
import com.launcher.api.link.ManagedDevicesRegistry
import com.launcher.api.paired.LocalLinkRevocationStore
import com.launcher.api.push.PushReceiver
import com.launcher.api.push.PushSender
import com.launcher.api.sync.RemoteSyncBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * **realBackend flavor** Koin wiring (FR-034, FR-035, T048b). Binds the
 * five spec-007 ports to Firebase + Cloudflare Worker implementations.
 *
 * **Identity role for spec 007**: this module wires
 * [FirebaseIdentityProvider] with [FirebaseIdentityProvider.Role.Managed]
 * because spec 007 only ships the Managed-side runtime. The admin-mode
 * scope (which would bind a second [IdentityProvider] with
 * [FirebaseIdentityProvider.Role.Admin]) lands in spec 009 admin flows
 * via a Koin scope override.
 *
 * Each binding is a `single` so observers see consistent state across the
 * process (matches "one Firestore project per user" assumption in
 * data-model.md §Relationships).
 *
 * **Runtime prerequisites** (not gated here — fail-fast at SDK init):
 *  - `google-services.json` in `:app/` so Firebase SDK auto-discovers
 *    config (currently committed for dev project `launcher-old-dev`).
 *  - Anonymous Auth enabled in Firebase Console
 *    (spec.md §Dependencies and prerequisites, manual click).
 */
val backendModule: Module = module {

    // Firebase SDK singletons. Default instance backs the dev project from
    // google-services.json. Production wiring lives in :app/realBackend.
    single { FirebaseFirestore.getInstance() }
    single { FirebaseAuth.getInstance() }
    single { FirebaseMessaging.getInstance() }

    // RemoteSyncBackend → Firestore.
    single<RemoteSyncBackend> { FirebaseRemoteSyncBackend(get()) }

    // IdentityProvider → Firebase Auth (anonymous). See kdoc above re role.
    single<IdentityProvider> {
        FirebaseIdentityProvider(
            auth = get(),
            role = FirebaseIdentityProvider.Role.Managed,
        )
    }

    // DeviceIdProvider → DataStore-backed UUIDv4.
    single<DeviceIdProvider> { DataStoreDeviceIdProvider(androidContext()) }

    // PushSender → Cloudflare Worker HTTPS POST /notify.
    single { FirebaseTokenSupplier(get()) }
    single<PushSender> { WorkerPushSender(tokenSupplier = get()) }

    // PushReceiver → log-based handler (full apply lands in spec 008/009).
    single<PushReceiver> { LauncherPushReceiver(backend = get()) }

    // FCM topic subscribe/unsubscribe — used internally by LinkRegistry.
    single { FcmRegistration(get()) }

    // LinkRegistry → Firestore subtree management + FCM topic lifecycle.
    // Spec 011 — also includes Firebase Storage cleanup в revoke (FR-043).
    single<LinkRegistry> {
        FirestoreLinkRegistry(
            backend = get(),
            firestore = get(),
            fcmRegistration = get(),
            encryptedMediaStorage = get(),
        )
    }

    // Spec 007 admin-side multi-link view via Firestore listener (separate
    // от single-link Managed LinkRegistry).
    single<ManagedDevicesRegistry> {
        FirestoreManagedDevicesRegistry(
            firestore = get(),
            auth = get(),
        )
    }

    // ─── Spec 011 — crypto repo + storage adapter wiring ──────────────────
    single<DeviceIdentityRepository> {
        FirestoreDeviceIdentityRepository(
            firestore = get(),
            asymmetric = get<AsymmetricCrypto>(),
            ownerUid = {
                // Resolved через IdentityProvider; current Firebase uid.
                get<IdentityProvider>().currentIdentity()?.firebaseAuthUid
            },
        )
    }

    // Spec 011 — Worker-proxied B2 blob storage (server-roadmap SRV-CRYPTO-001).
    // Credentials (B2 keyID/key) живут в Cloudflare Worker secrets, не на устройстве.
    // Симметричная авторизация — admin OR managed может upload/download.
    single<EncryptedMediaStorage> {
        WorkerEncryptedMediaStorage(
            tokenSupplier = get(),
        )
    }

    // ─── Spec 008 — bidirectional-config-sync wiring ──────────────────────

    // SQLDelight ConfigStore (KMP-pure data class; Android driver below).
    single<ConfigStore> { AndroidSqlDriverProvider.createConfigStore(androidContext()) }

    // LocalConfigStore → SQLDelight. Dispatchers.IO for disk operations
    // (FR-041/042; SC-004a sub-budget ≤ 50 ms p95 — Dispatchers.IO is the
    // canonical Android IO thread pool).
    single<LocalConfigStore> {
        SqlDelightLocalConfigStore(
            db = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    // Self-device-id supplier — provides synchronous access for FR-023
    // self-as-writer skip check. Pulls from DeviceIdProvider's hot Flow's
    // last-known value via runBlocking. DeviceIdProvider.currentDeviceId()
    // emits a stable UUID immediately on Flow subscribe (DataStore-backed).
    factory<() -> String>(named("selfDeviceId")) {
        val provider = get<DeviceIdProvider>()
        val supplier: () -> String = {
            runBlocking { provider.currentDeviceId().first() }
        }
        supplier
    }

    // ConfigApplier → Firebase. Reads /config/current, writes Local DB,
    // publishes /state/current (FR-021..023, FR-030..033).
    single<ConfigApplier> {
        FirebaseConfigApplier(
            remoteSync = get(),
            localStore = get(),
            selfDeviceIdProvider = get(named("selfDeviceId")),
        )
    }

    // Application-level coroutine scope для DefaultConfigEditor (push and
    // debounced autosave). Survives Activity recreation (state-management
    // checklist CHK010). Lifetime = process lifetime.
    single<CoroutineScope>(named("configEditorScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // ConfigEditor → optimistic-concurrency writer (FR-010..014, FR-040,
    // FR-054..057).
    single<ConfigEditor> {
        DefaultConfigEditor(
            remoteSync = get(),
            localStore = get(),
            selfDeviceIdProvider = get(named("selfDeviceId")),
            scope = get(named("configEditorScope")),
        )
    }

    // NetworkAvailability — Android ConnectivityManager wrapper (FR-022 T2).
    single<NetworkAvailability> { ConnectivityManagerNetworkAvailability(androidContext()) }

    // AppForegroundEvents — ProcessLifecycleOwner throttled (FR-022 T4).
    single<AppForegroundEvents> { ProcessLifecycleForegroundEvents() }

    // ─── Spec 010 — local-first revocation wiring (FR-032 / FR-032a) ─────

    // DataStore-backed flag set; survives kill/restart so the locally-revoked
    // link stays hidden even before the WorkManager cleanup worker runs.
    single<LocalLinkRevocationStore> { DataStoreLocalLinkRevocationStore(androidContext()) }

    // Custom WorkerFactory для DI-injected workers (ConfigRefreshWorker +
    // UnlinkCleanupWorker). App's Configuration.Provider implementation
    // references this via Koin.
    single {
        ConfigSyncWorkerFactory(
            linkRegistry = get(),
            configApplier = get(),
            revocationStore = get(),
        )
    }

    // ─── Spec 009 — admin-mode-flows wiring (Phase A) ─────────────────────

    // ConfigHistoryRepository → Firestore /links/{linkId}/configHistory.
    // FR-036/037/038. TODO(SRV-CONFIG-001): server-side write migration.
    single<ConfigHistoryRepository> { FirestoreConfigHistoryAdapter(firestore = get()) }

    // InstalledAppsCatalog → PackageManager.queryIntentActivities (FR-034).
    // Own package filtered via applicationId (mock flavour adds .mock suffix —
    // we use BuildConfig.APPLICATION_ID at the call site? Here we pass the
    // canonical id; if a future mock-flavour-specific filter is needed,
    // override via Koin scope).
    single<InstalledAppsCatalog> {
        InstalledAppsCatalogAdapter(
            context = androidContext(),
            ownPackageName = androidContext().packageName,
        )
    }

    // OpenAppDispatcher → 3-step fallback (FR-034/035/035a).
    single<OpenAppDispatcher> { OpenAppDispatcherAdapter(context = androidContext()) }

    // SystemContactPicker → ContactsContract resolver (FR-024). Two-step
    // protocol: UI launches ActivityResultContracts.PickContact directly;
    // adapter resolves the returned URI via SystemContactPickerAdapter.resolveUri.
    // UI consumes the concrete adapter (needs resolveUri()) — also bound
    // by port for tests + non-Android callers.
    single { SystemContactPickerAdapter(context = androidContext()) }
    single<SystemContactPicker> { get<SystemContactPickerAdapter>() }

    // VCardImporter → hand-written FN/TEL parser (FR-028, plan §5).
    single<VCardImporter> { VCardImporterAdapter() }

    // ─── Spec 010 ARCH-016 closure — HomeScreen reads /config/current ─────

    // FlowRepository → ProfileBackedFlowRepository (TASK-127 T127-024, FR-007).
    //
    // Was ConfigBackedFlowRepository, which read ConfigDocument — a model the
    // wizard stopped filling in TASK-126, so a fresh install landed on the
    // TASK-52 Error UI instead of tiles. The home screen now reads the same
    // Profile the wizard writes.
    //
    // ProfileStore is bound in presetModule (app) — same Koin container, so it
    // resolves here. ConfigDocument keeps serving the admin-push path
    // (SRV-CONFIG-DEPRECATION) until Profile-based sync replaces it.
    single<FlowRepository> {
        ProfileBackedFlowRepository(profileStore = get())
    }

    // ManagedDevicesRegistry → admin-side multi-link view via Firestore listener.
    single<ManagedDevicesRegistry> {
        FirestoreManagedDevicesRegistry(
            firestore = get(),
            auth = get(),
        )
    }

    // ─── Spec 017 (F-4 AuthProvider) — Google Sign-In + SessionStore ──────
    //
    // SessionStore — EncryptedSharedPreferences с TEE master key. Internal
    // visibility модификатор не пускает SessionRecord/SessionStore наружу
    // (consumer'ы видят только AuthProvider порт). Detekt rule T797 это
    // дополнительно enforces для cross-package leak'ов.

    single<com.launcher.api.auth.internal.SessionStore> {
        com.launcher.adapters.auth.EncryptedLocalSessionStore(
            context = androidContext(),
            json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }

    // GoogleSignInAuthAdapter — real Google Sign-In implementation.
    //
    // serverClientId = Web Application Client ID (OAuth client_type: 3) из
    // google-services.json текущего dev Firebase project (`launcher-old-dev`).
    // НЕ секретный — embedded в APK через google-services plugin, виден
    // любому reverse engineer'у. Безопасность держится на SHA-1 fingerprint
    // verification на стороне Google + Firebase Auth domain whitelist.
    //
    // TODO(server-roadmap SRV-AUTH-002, T901): перед production release
    // вытащить ID через BuildConfig.WEB_CLIENT_ID, генерируемый Gradle
    // plugin per-flavor (dev / staging / prod), чтобы не хардкодить
    // dev-project ID в коде. Сейчас acceptable потому что есть только
    // один Firebase project и production ещё не настроен.
    single {
        com.launcher.adapters.auth.GoogleSignInAuthAdapter(
            context = androidContext(),
            firebaseAuth = get(),
            firestore = get(),
            sessionStore = get(),
            serverClientId = "276980181074-ckqsoapcio17rfldredelv0tme3qm72m.apps.googleusercontent.com",
        )
    }

    // AuthProvider — выбирается AuthAdapterSelector по GmsAvailabilityPort.
    // GMS Available / Recoverable → GoogleSignInAuthAdapter.
    // GMS Fatal (Huawei) → NoSupportedAuthProvider.
    //
    // NB: pick() — suspend, потому через runBlocking. GMS-state проверяется
    // один раз при resolve этого singleton'а и кэшируется (CLAUDE.md §4 —
    // оверхед мизерный, переоценивать на каждый signIn не имеет смысла).
    single<com.launcher.api.auth.AuthProvider> {
        runBlocking {
            com.launcher.api.auth.AuthAdapterSelector(
                gmsAvailabilityPort = get(),
                realAdapterFactory = { get<com.launcher.adapters.auth.GoogleSignInAuthAdapter>() },
            ).pick()
        }
    }
}
