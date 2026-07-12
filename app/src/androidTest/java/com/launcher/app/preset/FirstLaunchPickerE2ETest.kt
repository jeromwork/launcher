package com.launcher.app.preset

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.app.preset.task120.PresetBootstrap
import com.launcher.preset.port.PoolSource
import com.launcher.preset.port.PresetSource
import com.launcher.preset.port.ProfileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * TASK-126 Phase 5 T093 — first-launch bundled-source E2E (US-1, SC-001).
 *
 * Verifies via the new ECS `PresetSource` + `PoolSource` bindings that:
 *   1. The bundled `simple-launcher` preset is loadable.
 *   2. `PresetBootstrap.bootstrap()` activates it end-to-end (Profile
 *      persisted to `ProfileStore`).
 *
 * The visual layer (PresetPickerScreen) is out of the ECS runtime scope —
 * TASK-126 is a runtime migration, not a UI redesign. Visual smoke is
 * owner-manual.
 */
@RunWith(AndroidJUnit4::class)
class FirstLaunchPickerE2ETest {

    @Test
    fun bundledPresetActivatesThroughEcsBootstrap() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        if (GlobalContext.getOrNull() == null) {
            app.javaClass.getMethod("onCreate").invoke(app)
        }
        val koin = GlobalContext.get()

        val presetSource: PresetSource = koin.get()
        val poolSource: PoolSource = koin.get()
        val bootstrap: PresetBootstrap = koin.get()
        val store: ProfileStore = koin.get()

        // The bundled catalogue must be non-empty and simple-launcher must be present.
        val ids = presetSource.listAvailable()
        assertTrue("bundled catalogue must list simple-launcher, got $ids", "simple-launcher" in ids)
        assertNotNull("pool must load", poolSource.loadPool())
        assertNotNull("simple-launcher preset must load", presetSource.loadPreset("simple-launcher"))

        val outcome = bootstrap.bootstrap()
        assertTrue(
            "bootstrap outcome must be Activated or AlreadyActive, got $outcome",
            outcome is PresetBootstrap.BootstrapOutcome.Activated ||
                outcome is PresetBootstrap.BootstrapOutcome.AlreadyActive,
        )
        assertNotNull("Profile must be persisted after bootstrap", store.load())
    }
}
