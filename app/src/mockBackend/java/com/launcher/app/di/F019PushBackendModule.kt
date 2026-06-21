package com.launcher.app.di

import family.keys.api.ConfigChangeNotifier
import family.keys.impl.NoOpConfigChangeNotifier
import family.push.api.BackgroundDispatcher
import family.push.api.FcmTokenPublisher
import family.push.api.FcmTokenPublisherError
import family.push.api.Outcome
import family.push.api.PushTrigger
import family.push.api.RetryStrategy
import family.push.impl.NullPushTrigger
import kotlin.time.Duration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.koin.dsl.module

/**
 * Spec 019 F-5c — **mockBackend flavor** bindings. Per CHK-DSS-007
 * (device-self-sufficiency): app работает локально без Firebase / Cloudflare.
 *
 *  • [PushTrigger] → [NullPushTrigger] — no-op, returns Success без сетевого вызова.
 *  • [FcmTokenPublisher] → no-op (publish ignored — никаких Firestore writes в mock).
 *  • [BackgroundDispatcher] → simple inline coroutine wrapper (no WorkManager).
 *  • [ConfigChangeNotifier] → [NoOpConfigChangeNotifier] — save success без
 *    каких-либо downstream-effects.
 *
 * Goal: mockBackend builds должен compile and run без Firebase SDK / Cloudflare
 * connectivity. F-5c push transport invisible to user in this flavor.
 */
val f019PushBackendModule = module {

    single<PushTrigger> { NullPushTrigger() }

    single<FcmTokenPublisher> {
        object : FcmTokenPublisher {
            override suspend fun publish(fcmToken: String): Outcome<Unit, FcmTokenPublisherError> =
                Outcome.Success(Unit)
        }
    }

    single<BackgroundDispatcher> {
        object : BackgroundDispatcher {
            override suspend fun dispatch(
                taskName: String,
                timeout: Duration,
                retryStrategy: RetryStrategy,
                block: suspend () -> Unit,
            ) {
                try {
                    withTimeout(timeout) { block() }
                } catch (_: TimeoutCancellationException) {
                    // Drop silently — mockBackend has no observability surface.
                }
            }
        }
    }

    single<ConfigChangeNotifier> { NoOpConfigChangeNotifier() }
}
