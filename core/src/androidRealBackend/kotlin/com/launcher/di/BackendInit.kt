package com.launcher.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * **realBackend flavor** Koin wiring (FR-034). Binds the five spec-007 ports
 * to Firebase + Cloudflare Worker implementations:
 *
 *  - `RemoteSyncBackend` → `FirebaseRemoteSyncBackend` (Firestore SDK)
 *  - `IdentityProvider`  → `FirebaseIdentityProvider` (Anonymous Auth)
 *  - `PushSender`        → `WorkerPushSender` (HTTPS → Cloudflare Worker)
 *  - `PushReceiver`      → `LauncherPushReceiver` (FCM data-message)
 *  - `LinkRegistry`      → `FirestoreLinkRegistry`
 *
 * **Skeleton in Phase 3 (T048a).** Bindings are filled in Phase 4 (T048b)
 * once the Firebase adapter classes land in `core/androidMain/adapters/`.
 * Until then this module is intentionally empty so the realBackend flavor
 * still **compiles** and `:app:assembleRealBackendDebug` produces an APK —
 * the APK will fail at runtime when any port is requested, which is fine
 * for the build-system milestone (Phase 3) but never released.
 *
 * TODO(spec 007 Phase 4 / T048b): wire the five impls listed above.
 */
val backendModule: Module = module {
    // TODO(Phase 4): real bindings.
}
