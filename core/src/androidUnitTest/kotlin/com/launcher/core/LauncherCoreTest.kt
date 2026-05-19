package com.launcher.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.adapters.config.ConfigBackedFlowRepository
import com.launcher.fake.config.FakeConfigEditor
import com.launcher.fake.config.FakeLocalConfigStore
import com.launcher.fake.link.FakeLinkRegistry
import com.launcher.fake.sync.FakeRemoteSyncBackend
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * Spec 010 T032a update: LauncherCore.flowRepository is now mandatory.
 * Test injects a `ConfigBackedFlowRepository` over the spec-007 / spec-008
 * fakes (FakeConfigEditor + FakeLinkRegistry) — мирро же продакшна без
 * Firebase / SQLDelight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherCoreTest {

    @Test
    fun startAndStopDoNotThrow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val localStore = FakeLocalConfigStore()
        val flowRepository = ConfigBackedFlowRepository(
            configEditor = FakeConfigEditor(
                localStore = localStore,
                selfDeviceId = "test-device",
            ),
            linkRegistry = FakeLinkRegistry(
                backend = FakeRemoteSyncBackend(),
                initial = null,
            ),
        )
        val core = LauncherCore(app, flowRepository = flowRepository, skipPackageScan = true)
        core.start()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        core.stop()
    }
}
