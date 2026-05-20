package com.launcher.adapters.setup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.SetupCheckContractTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 010 T074 — contract test for the battery-optimization check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BatteryOptimizationCheckAdapterTest : SetupCheckContractTest() {
    override fun createCheck(): SetupCheck =
        BatteryOptimizationCheckAdapter(context = ApplicationProvider.getApplicationContext<Application>())
}
