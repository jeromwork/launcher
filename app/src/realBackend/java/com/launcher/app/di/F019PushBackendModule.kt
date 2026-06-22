package com.launcher.app.di

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.launcher.app.push.FcmTokenBootstrapPublisher
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
import kotlinx.coroutines.tasks.await
import org.koin.core.qualifier.named
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

    // T131 (spec 019 FR-027) — после Sign-In + EnvelopeBootstrap success
    // LauncherApplication дёргает этот publisher. Получает свежий FCM token
    // через FirebaseMessaging и публикует в Firestore. Без этого вызова FCM
    // токен попадает в Firestore только при ротации (onNewToken), а первый
    // раз — никогда (токен уже выдан до Sign-In).
    single<FcmTokenBootstrapPublisher> {
        val publisher = get<FcmTokenPublisher>()
        FcmTokenBootstrapPublisher {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                if (token.isNullOrEmpty()) {
                    Log.w(TAG_FCM_BOOTSTRAP, "FirebaseMessaging.token returned null/empty — skip")
                    return@FcmTokenBootstrapPublisher
                }
                when (val r = publisher.publish(token)) {
                    is family.push.api.Outcome.Success ->
                        Log.i(TAG_FCM_BOOTSTRAP, "FCM token published (len=${token.length})")
                    is family.push.api.Outcome.Failure ->
                        Log.w(TAG_FCM_BOOTSTRAP, "FCM token publish failed: ${r.error.message}")
                }
            } catch (t: Throwable) {
                Log.w(TAG_FCM_BOOTSTRAP, "FCM token fetch/publish error: ${t.message}")
            }
        }
    }

    single<ConfigChangeNotifier> {
        PushTriggerConfigChangeNotifier(
            pushTrigger = get<PushTrigger>(),
            identity = get<IdentityProof>(),
        )
    }
}

private const val TAG_FCM_BOOTSTRAP = "F5cTokenBootstrap"
