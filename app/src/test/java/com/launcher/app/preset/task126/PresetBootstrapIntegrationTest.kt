package com.launcher.app.preset.task126

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.app.di.task120Module
import com.launcher.app.preset.task120.PresetBootstrap
import com.launcher.app.wizard.WizardViewModel
import com.launcher.preset.engine.ReconcileEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T058 — verifies the TASK-120 Koin graph resolves the full preset stack
 * (`PresetBootstrap`, `ReconcileEngine`, `WizardViewModel`) without missing
 * bindings or circular dependencies, and that `bootstrap()` completes
 * synchronously on a clean install (mirroring what `FirstLaunchActivity`
 * does before it launches `WizardHostActivity`).
 *
 * Uses a bare `Application` (not `LauncherApplication`) so we start Koin
 * ourselves with only `task120Module` — the prod graph pulls Firebase / crypto
 * dependencies that aren't available in JVM unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PresetBootstrapIntegrationTest {

    @After fun tearDown() {
        if (GlobalContext.getOrNull() != null) stopKoin()
    }

    @Test
    fun task120Graph_resolvesPresetStack() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        val koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(task120Module)
        }.koin

        val bootstrap = koin.get<PresetBootstrap>()
        val engine = koin.get<ReconcileEngine>()
        val viewModel = koin.get<WizardViewModel>()

        assertNotNull(bootstrap)
        assertNotNull(engine)
        assertNotNull(viewModel)
    }

    @Test
    fun bootstrap_completes_onFreshInstall() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        val koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(task120Module)
        }.koin
        val bootstrap = koin.get<PresetBootstrap>()

        val outcome = runBlocking { bootstrap.bootstrap() }

        assertTrue(
            "Expected Activated or AlreadyActive, got $outcome",
            outcome is PresetBootstrap.BootstrapOutcome.Activated ||
                outcome is PresetBootstrap.BootstrapOutcome.AlreadyActive,
        )
    }
}
