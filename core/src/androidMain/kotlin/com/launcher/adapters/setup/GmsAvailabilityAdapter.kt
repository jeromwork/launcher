package com.launcher.adapters.setup

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real adapter for spec 010 [GmsAvailabilityPort] (FR-042..FR-044).
 *
 * Wraps `GoogleApiAvailability.isGooglePlayServicesAvailable()` and maps the
 * `ConnectionResult` integer codes into the domain sealed [GmsStatus].
 *
 * Mapping (per Google API documentation):
 *  - [ConnectionResult.SUCCESS] → [GmsStatus.Available]
 *  - [ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED]      → MissingRecoverable
 *  - [ConnectionResult.SERVICE_UPDATING]                      → MissingRecoverable
 *  - [ConnectionResult.SERVICE_DISABLED]                      → MissingRecoverable
 *  - [ConnectionResult.SERVICE_MISSING]                       → MissingFatal
 *  - [ConnectionResult.SERVICE_INVALID]                       → MissingFatal
 *  - any other non-success code                               → MissingFatal
 *
 * `isUserResolvableError()` is consulted to know whether a system dialog is
 * available for the FR-044 recovery path.
 */
class GmsAvailabilityAdapter(
    private val context: Context,
    private val api: GoogleApiAvailability = GoogleApiAvailability.getInstance(),
) : GmsAvailabilityPort {

    override suspend fun status(): GmsStatus = withContext(Dispatchers.IO) {
        val code = api.isGooglePlayServicesAvailable(context)
        when (code) {
            ConnectionResult.SUCCESS -> GmsStatus.Available

            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ConnectionResult.SERVICE_UPDATING,
            ConnectionResult.SERVICE_DISABLED -> GmsStatus.MissingRecoverable(
                reason = "gms_recoverable_$code",
                resolutionAvailable = api.isUserResolvableError(code),
            )

            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_INVALID -> GmsStatus.MissingFatal(
                reason = "gms_fatal_$code",
            )

            else -> if (api.isUserResolvableError(code)) {
                GmsStatus.MissingRecoverable(
                    reason = "gms_recoverable_$code",
                    resolutionAvailable = true,
                )
            } else {
                GmsStatus.MissingFatal(reason = "gms_fatal_$code")
            }
        }
    }
}
