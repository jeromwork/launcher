package com.launcher.adapters.setup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.SetupCheckContractTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 010 T037 — verifies RoleHomeCheckAdapter satisfies the SetupCheck
 * contract on Robolectric (default SDK = 33 which exercises the
 * RoleManager.isRoleHeld branch).
 *
 * The actual emulator smoke (API 26-28 legacy fallback path) lives в T051
 * (TODO[physical-device] / TODO[emulator-session]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoleHomeCheckAdapterTest : SetupCheckContractTest() {
    override fun createCheck(): SetupCheck =
        RoleHomeCheckAdapter(context = ApplicationProvider.getApplicationContext<Application>())
}
