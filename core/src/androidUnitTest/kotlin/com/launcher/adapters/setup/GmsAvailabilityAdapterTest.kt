package com.launcher.adapters.setup

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.launcher.api.setup.GmsStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 010 T006: maps `ConnectionResult` codes from [GoogleApiAvailability]
 * into the domain [GmsStatus] sealed.
 */
class GmsAvailabilityAdapterTest {

    private val context = mockk<Context>(relaxed = true)

    private fun adapterReturning(code: Int, resolvable: Boolean = false): GmsAvailabilityAdapter {
        val api = mockk<GoogleApiAvailability>()
        every { api.isGooglePlayServicesAvailable(context) } returns code
        every { api.isUserResolvableError(code) } returns resolvable
        return GmsAvailabilityAdapter(context, api)
    }

    @Test
    fun success_maps_to_Available() = runTest {
        val status = adapterReturning(ConnectionResult.SUCCESS).status()
        assertEquals(GmsStatus.Available, status)
    }

    @Test
    fun service_version_update_required_maps_to_Recoverable() = runTest {
        val status = adapterReturning(
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, resolvable = true,
        ).status()
        assertTrue(status is GmsStatus.MissingRecoverable)
        assertTrue((status as GmsStatus.MissingRecoverable).resolutionAvailable)
    }

    @Test
    fun service_updating_maps_to_Recoverable() = runTest {
        val status = adapterReturning(ConnectionResult.SERVICE_UPDATING).status()
        assertTrue(status is GmsStatus.MissingRecoverable)
    }

    @Test
    fun service_disabled_maps_to_Recoverable() = runTest {
        val status = adapterReturning(ConnectionResult.SERVICE_DISABLED).status()
        assertTrue(status is GmsStatus.MissingRecoverable)
    }

    @Test
    fun service_missing_maps_to_Fatal() = runTest {
        val status = adapterReturning(ConnectionResult.SERVICE_MISSING).status()
        assertTrue(status is GmsStatus.MissingFatal)
    }

    @Test
    fun service_invalid_maps_to_Fatal() = runTest {
        val status = adapterReturning(ConnectionResult.SERVICE_INVALID).status()
        assertTrue(status is GmsStatus.MissingFatal)
    }

    @Test
    fun unknown_resolvable_code_maps_to_Recoverable() = runTest {
        // 9999 is not a defined ConnectionResult constant.
        val status = adapterReturning(9999, resolvable = true).status()
        assertTrue(status is GmsStatus.MissingRecoverable)
    }

    @Test
    fun unknown_non_resolvable_code_maps_to_Fatal() = runTest {
        val status = adapterReturning(9998, resolvable = false).status()
        assertTrue(status is GmsStatus.MissingFatal)
    }
}
