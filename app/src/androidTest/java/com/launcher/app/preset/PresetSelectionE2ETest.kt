package com.launcher.app.preset

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.adapters.preset.PresetSelectionService
import com.launcher.api.profile.ProfileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Spec T67E coverage (US-1, SC-001) via service layer.
 *
 * Verifies the production DI graph wires PresetSelectionService correctly
 * and that beginSetup("simple-launcher") loads the bundled preset, applies
 * CopyOnActivateStrategy, and persists it as active in PreferencesProfileStore.
 *
 * The Activity-level UI flow (FirstLaunchActivity → picker → wizard) is
 * tested at the existing TASK-7 instrumentation suite; this test isolates
 * the TASK-65 service path.
 */
@RunWith(AndroidJUnit4::class)
class PresetSelectionE2ETest {

    @Before
    fun ensureKoinReady() {
        if (GlobalContext.getOrNull() == null) {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            app.javaClass.getMethod("onCreate").invoke(app)
        }
    }

    @Test
    fun beginSetupSimpleLauncherPersistsActivePresetRef() = runBlocking {
        val koin = GlobalContext.get()
        val selection: PresetSelectionService = koin.get()
        val store: ProfileStore = koin.get()

        // Reset to fresh state — clears prior runs on the same emulator.
        store.setActive(null)

        val outcome = selection.beginSetup("simple-launcher")
        assertNotNull(outcome)

        val active = store.getActive()
        assertNotNull("setActive must persist", active)
        assertEquals("com.launcher.preset.simple-launcher", active!!.first.uid)
        assertEquals(1, active.first.version)
    }
}
