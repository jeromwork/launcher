package com.launcher.app.di

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.launcher.app.auth.FcmTokenRegistrationGuard
import com.launcher.app.di.CLOUD_SCOPE_QUALIFIER
import com.launcher.app.push.FcmTokenBootstrapPublisher
import com.launcher.adapters.push.FirebaseTokenSupplier
import com.launcher.app.data.identity.IdentityCacheInvalidator
import com.launcher.app.push.FcmTokenPublisherImpl
import com.launcher.app.push.FirebaseIdTokenProviderAdapter
import com.launcher.app.push.PushTriggerConfigChangeNotifier
import com.launcher.cloud.api.CloudAvailability
import family.keys.api.ConfigChangeNotifier
import family.keys.api.IdentityProof
import family.keys.api.internal.DeviceIdentity
import family.push.android.HttpPushTrigger
import family.push.android.WorkManagerBackgroundDispatcher
import family.push.api.BackgroundDispatcher
import family.push.api.FcmTokenPublisher
import family.push.api.IdTokenProvider
import family.push.api.PushTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import org.koin.core.context.GlobalContext
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

    // task-6 wiring 2026-06-30: shared FirebaseTokenSupplier singleton so the
    // post-Sign-In invalidate() hook in LauncherApplication evicts the same
    // cache the IdTokenProvider reads from. Without this, two distinct
    // instances would diverge — invalidate() would no-op and the next backup
    // upload would still send the stale token without claims.stableId.
    single { FirebaseTokenSupplier() }

    single<IdTokenProvider> { FirebaseIdTokenProviderAdapter(tokenSupplier = get()) }

    // task-6 wiring 2026-06-30 (T681-FOLLOWUP) — bridge from the IdentityCache-
    // Invalidator port (consumed in app/src/main by LauncherApplication's
    // post-Sign-In hook) onto the same FirebaseTokenSupplier singleton above.
    single<IdentityCacheInvalidator> {
        val supplier = get<FirebaseTokenSupplier>()
        IdentityCacheInvalidator { supplier.invalidate() }
    }

    single<PushTrigger> {
        HttpPushTrigger.create(idTokenProvider = get<IdTokenProvider>())
    }

    // Raw publisher (Firestore writer). Internal — wrapped by guard below.
    single<FcmTokenPublisher>(named(FCM_PUBLISHER_RAW)) {
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

    // TASK-49 T028+T029 — gates all FCM writes on CloudAvailability + observes
    // `false→true` transition to re-publish current token. All callers
    // (`LauncherFirebaseMessagingService.onNewToken`, `FcmTokenBootstrapPublisher`)
    // resolve `FcmTokenPublisher` from Koin — they transparently get the guard.
    single<FcmTokenPublisher> {
        FcmTokenRegistrationGuard(
            inner = get(named(FCM_PUBLISHER_RAW)),
            cloudAvailability = get<CloudAvailability>(),
            scope = get<CoroutineScope>(named(CLOUD_SCOPE_QUALIFIER)),
            publishCurrentToken = {
                // Lazy resolution breaks Koin cycle: guard → bootstrap → publisher → guard.
                GlobalContext.get().get<FcmTokenBootstrapPublisher>().publishCurrent()
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
        // Uses raw publisher: guard's transition observer fires THIS bootstrap,
        // so going back through guard would loop (or skip on cloudAvailable=false
        // during sign-out, where bootstrap shouldn't fire anyway).
        val publisher = get<FcmTokenPublisher>(named(FCM_PUBLISHER_RAW))
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
private const val FCM_PUBLISHER_RAW = "task49.fcm.publisher.raw"
