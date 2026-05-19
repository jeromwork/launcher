package com.launcher.adapters.setup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.SetupCheckContractTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 010 T048 — contract test on Robolectric (SDK 33 to exercise the
 * runtime-permission branch). The API < 33 skip path is covered by the
 * adapter's own kdoc + integration tests in Phase 5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PostNotificationsCheckAdapterTest : SetupCheckContractTest() {
    override fun createCheck(): SetupCheck =
        PostNotificationsCheckAdapter(context = ApplicationProvider.getApplicationContext<Application>())
}
