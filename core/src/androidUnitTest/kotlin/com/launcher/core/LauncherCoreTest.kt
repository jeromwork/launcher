package com.launcher.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherCoreTest {

    @Test
    fun startAndStopDoNotThrow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val core = LauncherCore(app, skipPackageScan = true)
        core.start()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        core.stop()
    }
}
