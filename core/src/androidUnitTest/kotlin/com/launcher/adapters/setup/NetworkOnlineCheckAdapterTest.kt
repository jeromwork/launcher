package com.launcher.adapters.setup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.SetupCheckContractTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 010 T038 — verifies NetworkOnlineCheckAdapter satisfies the SetupCheck
 * contract on Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkOnlineCheckAdapterTest : SetupCheckContractTest() {
    override fun createCheck(): SetupCheck =
        NetworkOnlineCheckAdapter(context = ApplicationProvider.getApplicationContext<Application>())
}
