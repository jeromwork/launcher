package com.launcher.di

import com.launcher.api.config.ConfigApplier
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.identity.IdentityProvider
import com.launcher.adapters.lifecycle.ConfigSyncWorkerFactory
import com.launcher.api.lifecycle.AppForegroundEvents
import com.launcher.api.lifecycle.NetworkAvailability
import com.launcher.api.link.LinkRegistry
import com.launcher.api.push.PushReceiver
import com.launcher.api.push.PushSender
import com.launcher.api.sync.RemoteSyncBackend
import com.launcher.fake.config.FakeConfigApplier
import com.launcher.fake.config.FakeConfigEditor
import com.launcher.fake.config.FakeLocalConfigStore
import com.launcher.fake.identity.FakeDeviceIdProvider
import com.launcher.fake.identity.FakeIdentityProvider
import com.launcher.fake.lifecycle.FakeAppForegroundEvents
import com.launcher.fake.lifecycle.FakeNetworkAvailability
import com.launcher.fake.link.FakeLinkRegistry
import com.launcher.fake.push.FakePushReceiver
import com.launcher.fake.push.FakePushSender
import com.launcher.fake.sync.FakeRemoteSyncBackend
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * **mockBackend flavor** Koin wiring (FR-034, FR-035). Binds all spec-007
 * ports to the Fake adapters from `core/commonMain/com/launcher/fake/` so the
 * app builds and runs **without `google-services.json`, without Firebase
 * deps, without a Cloudflare account**.
 *
 * Used by CI smoke checks (no provisioning), by developers who don't have
 * Firebase credentials yet, and by the on-device QA flow that needs
 * predictable in-memory state.
 *
 * Each Fake is bound as a `single` so observers see consistent state across
 * the process (matches the "one Firestore project" assumption per
 * data-model.md §Relationships). Identity uses [FakeIdentityProvider.Role.Managed]
 * by default; admin-side scope override happens in `:app/.../admin/` (spec 009
 * future); for spec 007 we only need the Managed side end-to-end.
 */
val backendModule: Module = module {
    single<RemoteSyncBackend> { FakeRemoteSyncBackend() }
    single<IdentityProvider> {
        FakeIdentityProvider(
            role = FakeIdentityProvider.Role.Managed,
            seedUid = "fake-managed-uid-0001",
        )
    }
    single<DeviceIdProvider> { FakeDeviceIdProvider() }
    single<LinkRegistry> { FakeLinkRegistry(backend = get()) }
    single<PushSender> { FakePushSender() }
    single<PushReceiver> { FakePushReceiver() }

    // ─── Spec 008 mockBackend wiring ──────────────────────────────────────

    single<LocalConfigStore> { FakeLocalConfigStore() }
    single<ConfigApplier> {
        FakeConfigApplier(
            localStore = get(),
            selfDeviceId = "fake-managed-uid-0001",
        )
    }
    single<ConfigEditor> {
        FakeConfigEditor(
            localStore = get(),
            selfDeviceId = "fake-managed-uid-0001",
        )
    }
    single<NetworkAvailability> { FakeNetworkAvailability() }
    single<AppForegroundEvents> { FakeAppForegroundEvents() }

    // WorkerFactory — needed by Application.Configuration.Provider even в
    // mockBackend flavor (WorkManager itself is still active в mockBackend).
    single { ConfigSyncWorkerFactory(linkRegistry = get(), configApplier = get()) }
}
