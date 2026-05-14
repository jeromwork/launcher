package com.launcher.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.launcher.adapters.config.AndroidSqlDriverProvider
import com.launcher.adapters.config.DefaultConfigEditor
import com.launcher.adapters.config.FirebaseConfigApplier
import com.launcher.adapters.config.SqlDelightLocalConfigStore
import com.launcher.adapters.identity.DataStoreDeviceIdProvider
import com.launcher.adapters.identity.FirebaseIdentityProvider
import com.launcher.adapters.lifecycle.ConnectivityManagerNetworkAvailability
import com.launcher.adapters.lifecycle.ProcessLifecycleForegroundEvents
import com.launcher.adapters.link.FirestoreLinkRegistry
import com.launcher.adapters.push.FcmRegistration
import com.launcher.adapters.push.FirebaseTokenSupplier
import com.launcher.adapters.push.LauncherPushReceiver
import com.launcher.adapters.push.WorkerPushSender
import com.launcher.adapters.sync.FirebaseRemoteSyncBackend
import com.launcher.adapters.config.db.ConfigStore
import com.launcher.api.config.ConfigApplier
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.lifecycle.AppForegroundEvents
import com.launcher.api.lifecycle.NetworkAvailability
import com.launcher.api.link.LinkRegistry
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
    single<LinkRegistry> {
        FirestoreLinkRegistry(
            backend = get(),
            firestore = get(),
            fcmRegistration = get(),
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
}
