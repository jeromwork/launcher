package com.launcher.di

/**
 * Marker file for spec 007 backend wiring. The **actual** Koin module is
 * provided per flavor by `BackendInit.kt` in
 * `core/src/androidMainRealBackend/` (Firebase + Cloudflare Worker, FR-034)
 * and `core/src/androidMainMockBackend/` (Fake adapters, FR-034/FR-035).
 *
 * Both flavors expose the symbol [backendModule] in this package. Consumers
 * (e.g. `LauncherApplication`) import it once; AGP's variant matching picks
 * the right source-set based on the active product flavor.
 *
 * If a future port has a **flavor-agnostic** Android adapter (e.g.
 * `DataStoreDeviceIdProvider` reads DataStore the same way regardless of
 * backend), declare it here and reference it from both flavor's
 * `backendModule { includes(...) }` to avoid duplication.
 *
 * TODO(spec 007 Phase 4): add `DataStoreDeviceIdProvider` binding here when
 * the Android-side `IdentityCache` lands (T056).
 */
@Suppress("unused")
private const val BACKEND_MODULE_FLAVOR_MARKER: String = "see BackendInit.kt per flavor"
