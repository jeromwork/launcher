package com.launcher.di

import com.launcher.adapters.config.ConfigBackedFlowRepository
import com.launcher.api.FlowRepository
import com.launcher.api.apps.InstalledApp
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
import com.launcher.adapters.lifecycle.ConfigSyncWorkerFactory
import com.launcher.api.lifecycle.AppForegroundEvents
import com.launcher.api.lifecycle.NetworkAvailability
import com.launcher.api.link.LinkRegistry
import com.launcher.api.link.ManagedDevicesRegistry
import com.launcher.api.paired.LocalLinkRevocationStore
import com.launcher.api.push.PushReceiver
import com.launcher.api.push.PushSender
import com.launcher.api.sync.RemoteSyncBackend
import cryptokit.pairing.api.DeviceIdentityRepository
import cryptokit.pairing.api.EncryptedMediaStorage
import com.launcher.fake.apps.FakeInstalledAppsCatalog
import com.launcher.fake.crypto.InMemoryDeviceIdentityRepository
import com.launcher.fake.crypto.InMemoryEncryptedMediaStorage
import com.launcher.fake.apps.FakeOpenAppDispatcher
import com.launcher.fake.config.FakeConfigApplier
import com.launcher.fake.config.FakeConfigEditor
import com.launcher.fake.config.FakeLocalConfigStore
import com.launcher.fake.contacts.FakeSystemContactPicker
import com.launcher.fake.contacts.FakeVCardImporter
import com.launcher.fake.history.FakeConfigHistoryRepository
import com.launcher.fake.identity.FakeDeviceIdProvider
import com.launcher.fake.identity.FakeIdentityProvider
import com.launcher.fake.lifecycle.FakeAppForegroundEvents
import com.launcher.fake.lifecycle.FakeNetworkAvailability
import com.launcher.fake.link.FakeLinkRegistry
import com.launcher.fake.link.FakeManagedDevicesRegistry
import com.launcher.fake.paired.InMemoryLocalLinkRevocationStore
import com.launcher.fake.push.FakePushReceiver
import com.launcher.fake.push.FakePushSender
import com.launcher.fake.sync.FakeRemoteSyncBackend
import com.launcher.api.result.Outcome
import com.launcher.api.contacts.ImportError
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
    // Spec 009 G10 — seed mockBackend с pre-paired link so the admin
    // entry в Settings → "Сопряжённые устройства" surfaces a device row,
    // and Editor / History / Contacts / Health navigation is reachable
    // without going through the real pairing flow. Real backend stays
    // empty until QR-pair completes.
    single<LinkRegistry> {
        FakeLinkRegistry(
            backend = get(),
            initial = com.launcher.api.link.Link(
                linkId = "mock-link-0001",
                adminId = com.launcher.api.identity.AdminIdentity("fake-admin-uid-0001"),
                managedDeviceId = "mock-managed-device-0001",
                managedDeviceFirebaseUid = "fake-managed-uid-0001",
                createdAt = 1747166400000L,
            ),
        )
    }
    // Spec 007 admin-side multi-link view (separate from single-link LinkRegistry).
    single<ManagedDevicesRegistry> { FakeManagedDevicesRegistry() }
    single<PushSender> { FakePushSender() }
    single<PushReceiver> { FakePushReceiver() }

    // ─── Spec 011 mockBackend wiring ──────────────────────────────────────
    single<DeviceIdentityRepository> { InMemoryDeviceIdentityRepository() }
    single<EncryptedMediaStorage> { InMemoryEncryptedMediaStorage() }

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

    // ─── Spec 010 mockBackend wiring ─────────────────────────────────────

    // FR-032 local revocation store: in-memory for mockBackend — survives
    // process for the session but not across reinstalls (acceptable in QA).
    single<LocalLinkRevocationStore> { InMemoryLocalLinkRevocationStore() }

    // WorkerFactory — needed by Application.Configuration.Provider even в
    // mockBackend flavor (WorkManager itself is still active в mockBackend).
    single {
        ConfigSyncWorkerFactory(
            linkRegistry = get(),
            configApplier = get(),
            revocationStore = get(),
        )
    }

    // ─── Spec 009 mockBackend wiring (Phase A) ────────────────────────────

    single<ConfigHistoryRepository> { FakeConfigHistoryRepository() }
    single<InstalledAppsCatalog> {
        FakeInstalledAppsCatalog(
            apps = listOf(
                InstalledApp(packageName = "com.whatsapp", label = "WhatsApp", iconResource = null),
                InstalledApp(packageName = "org.telegram.messenger", label = "Telegram", iconResource = null),
                InstalledApp(packageName = "com.google.android.youtube", label = "YouTube", iconResource = null),
            ),
        )
    }
    single<OpenAppDispatcher> { FakeOpenAppDispatcher() }
    single<SystemContactPicker> { FakeSystemContactPicker() }
    // VCardImporter fake needs a default scripted RawVCard — mock-mode share
    // flow uses this. Tests scope their own instance with custom scripted.
    single<VCardImporter> {
        FakeVCardImporter(
            scripted = Outcome.Failure(ImportError.MissingFn),
        )
    }

    // ─── Spec 010 ARCH-016 closure — HomeScreen reads /config/current ─────

    // FlowRepository → ConfigBackedFlowRepository (replaces deleted
    // MockFlowRepository). Reads layout reactively from FakeConfigEditor's
    // observeAppliedConfig, mapping Slot → Action via SlotToActionMapper.
    // No bundled flows_mock_*.json — empty until QA/test seeds applied config.
    single<FlowRepository> {
        ConfigBackedFlowRepository(
            configEditor = get(),
            linkRegistry = get(),
            revocationStore = get(),
        )
    }

    // Spec 017 (F-4 AuthProvider) — mockBackend wires FakeAuthProvider.
    // Сидит в commonMain (не commonTest), чтобы быть видимым из production
    // APK mockBackend flavor'а. Реальный Google adapter подтягивается
    // только в realBackend (см. backendModule в androidRealBackend).
    single<com.launcher.api.auth.AuthProvider> {
        com.launcher.fake.auth.FakeAuthProvider()
    }
}
