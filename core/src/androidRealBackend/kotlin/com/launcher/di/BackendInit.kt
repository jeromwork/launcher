package com.launcher.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.launcher.adapters.identity.DataStoreDeviceIdProvider
import com.launcher.adapters.identity.FirebaseIdentityProvider
import com.launcher.adapters.link.FirestoreLinkRegistry
import com.launcher.adapters.push.FcmRegistration
import com.launcher.adapters.push.FirebaseTokenSupplier
import com.launcher.adapters.push.LauncherPushReceiver
import com.launcher.adapters.push.WorkerPushSender
import com.launcher.adapters.sync.FirebaseRemoteSyncBackend
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.link.LinkRegistry
import com.launcher.api.push.PushReceiver
import com.launcher.api.push.PushSender
import com.launcher.api.sync.RemoteSyncBackend
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
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
}
