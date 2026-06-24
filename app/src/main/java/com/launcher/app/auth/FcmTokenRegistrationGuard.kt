package com.launcher.app.auth

import android.util.Log
import com.launcher.app.push.FcmTokenBootstrapPublisher
import com.launcher.cloud.api.CloudAvailability
import family.push.api.FcmTokenPublisher
import family.push.api.FcmTokenPublisherError
import family.push.api.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * TASK-49 T028 — Decorator over [FcmTokenPublisher] that gates Firestore
 * registration on [CloudAvailability]. Implements FR-013 (FCM token MUST NOT
 * be written to Firestore when `cloudAvailable=false`) and FR-014 (existing
 * registrations untouched — guard only intercepts new writes).
 *
 * Behaviour:
 *  • [publish] (and the equivalent [registerIfAllowed]) → checks
 *    [CloudAvailability.isCloudAvailable] synchronously; if `false`, returns
 *    [Outcome.Success] without touching backend; if `true`, delegates to
 *    the wrapped [inner].
 *  • Observer on [CloudAvailability.isCloudAvailableFlow]: on `false → true`
 *    transition (e.g. user just signed in), triggers
 *    [publishCurrentToken] so that the device's current FCM token gets
 *    registered without waiting for the next [FirebaseMessagingService.onNewToken]
 *    rotation. The lambda usually resolves to
 *    [FcmTokenBootstrapPublisher.publishCurrent].
 */
class FcmTokenRegistrationGuard(
    private val inner: FcmTokenPublisher,
    private val cloudAvailability: CloudAvailability,
    scope: CoroutineScope,
    private val publishCurrentToken: suspend () -> Unit,
) : FcmTokenPublisher {

    init {
        scope.launch {
            var previous: Boolean? = null
            cloudAvailability.isCloudAvailableFlow.collect { current ->
                if (previous == false && current) {
                    runCatching { publishCurrentToken() }
                        .onFailure { Log.w(TAG, "transition publish failed: ${it.message}") }
                }
                previous = current
            }
        }
    }

    override suspend fun publish(fcmToken: String): Outcome<Unit, FcmTokenPublisherError> =
        registerIfAllowed(fcmToken)

    suspend fun registerIfAllowed(token: String): Outcome<Unit, FcmTokenPublisherError> {
        if (!cloudAvailability.isCloudAvailable()) {
            Log.d(TAG, "cloudAvailable=false — FCM token registration skipped")
            return Outcome.Success(Unit)
        }
        return inner.publish(token)
    }

    private companion object {
        const val TAG = "FcmTokenGuard"
    }
}
