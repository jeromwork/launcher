package com.launcher.app.di

import com.google.firebase.firestore.FirebaseFirestore
import com.launcher.app.push.FcmTokenPublisherImpl
import com.launcher.app.push.FirebaseIdTokenProviderAdapter
import com.launcher.app.push.PushTriggerConfigChangeNotifier
import family.keys.api.ConfigChangeNotifier
import family.keys.api.IdentityProof
import family.keys.api.internal.DeviceIdentity
import family.push.android.HttpPushTrigger
import family.push.android.WorkManagerBackgroundDispatcher
import family.push.api.BackgroundDispatcher
import family.push.api.FcmTokenPublisher
import family.push.api.IdTokenProvider
import family.push.api.PushTrigger
import org.koin.dsl.module

/**
 * Spec 019 F-5c — **realBackend flavor** bindings.
 *
 *  • [PushTrigger] → [family.push.impl.DefaultPushTrigger] wrapping Ktor +
 *    [FirebaseIdTokenProviderAdapter].
 *  • [FcmTokenPublisher] → [FcmTokenPublisherImpl] (Firestore merge write).
 *  • [BackgroundDispatcher] → [WorkManagerBackgroundDispatcher].
 *  • [ConfigChangeNotifier] → [PushTriggerConfigChangeNotifier] (bridges
 *    F-5b save success → F-5c push trigger). **Triggered automatically** by
 *    EnvelopeAsyncPushWorker Koin GlobalContext lookup after successful put.
 *
 * Per CLAUDE.md rule 2 (ACL): Firebase SDK is imported ONLY in адаптеры and
 * this DI module. Foundation `:core:push` остаётся vendor-clean.
 */
val f019PushBackendModule = module {

    single<IdTokenProvider> { FirebaseIdTokenProviderAdapter() }

    single<PushTrigger> {
        HttpPushTrigger.create(idTokenProvider = get<IdTokenProvider>())
    }

    single<FcmTokenPublisher> {
        FcmTokenPublisherImpl(
            firestore = FirebaseFirestore.getInstance(),
            uidSupplier = {
                get<IdentityProof>().currentIdentity()?.stableId?.takeIf { it.isNotEmpty() }
            },
            // DeviceIdentity already wired в f018KeysBackendModule. We pull its
            // thisDeviceId via the same Koin binding.
            deviceIdSupplier = {
                runCatching { get<DeviceIdentity>().thisDeviceId().value }.getOrNull()
            },
        )
    }

    single<BackgroundDispatcher> { WorkManagerBackgroundDispatcher() }

    single<ConfigChangeNotifier> {
        PushTriggerConfigChangeNotifier(
            pushTrigger = get<PushTrigger>(),
            identity = get<IdentityProof>(),
        )
    }
}
