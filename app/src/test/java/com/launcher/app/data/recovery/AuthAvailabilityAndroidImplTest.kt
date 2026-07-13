package com.launcher.app.data.recovery

import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import cryptokit.keys.api.AuthAvailabilityStatus
import cryptokit.keys.api.AvailabilityReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for [AuthAvailabilityAndroidImpl] (T641, FR-013).
 *
 * Verifies the F-4 [GmsStatus] → F-5 [AuthAvailabilityStatus] mapping is correct
 * for all three GMS states. Pure JVM — no Robolectric needed because the adapter
 * has no Android-platform dependencies (only domain ports).
 */
class AuthAvailabilityAndroidImplTest {

    private fun adapter(status: GmsStatus): AuthAvailabilityAndroidImpl =
        AuthAvailabilityAndroidImpl(StubGmsPort(status))

    @Test
    fun gmsAvailableMapsToAvailable() = runTest {
        val result = adapter(GmsStatus.Available).check()
        assertTrue("Expected Available, got $result", result is AuthAvailabilityStatus.Available)
    }

    @Test
    fun gmsMissingRecoverableMapsToAvailable() = runTest {
        // MissingRecoverable carries the recovery dialog through F-4's
        // signIn() flow — from F-5's perspective the provider remains usable.
        val result = adapter(
            GmsStatus.MissingRecoverable(reason = "gms_outdated", resolutionAvailable = true)
        ).check()
        assertTrue("Expected Available, got $result", result is AuthAvailabilityStatus.Available)
    }

    @Test
    fun gmsMissingFatalMapsToUnavailableWithNoSupportedProvider() = runTest {
        val result = adapter(GmsStatus.MissingFatal(reason = "gms_service_invalid")).check()
        assertTrue("Expected Unavailable, got $result", result is AuthAvailabilityStatus.Unavailable)
        assertEquals(AvailabilityReason.NoSupportedProvider, (result as AuthAvailabilityStatus.Unavailable).reason)
    }

    private class StubGmsPort(private val state: GmsStatus) : GmsAvailabilityPort {
        override suspend fun status(): GmsStatus = state
    }
}
