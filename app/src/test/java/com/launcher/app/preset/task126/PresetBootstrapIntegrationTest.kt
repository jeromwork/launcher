package com.launcher.app.preset.task126

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import com.launcher.app.di.presetModule
import com.launcher.app.preset.task120.PresetBootstrap
import com.launcher.app.wizard.WizardViewModel
import com.launcher.preset.engine.ReconcileEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.koin.dsl.module
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
 * ourselves with only `presetModule` — the prod graph pulls Firebase / crypto
 * dependencies that aren't available in JVM unit tests.
 *
 * TASK-127: `PresetBootstrap` now also needs `PresetRepository` (to honour the
 * preset the user picked). In production that binding comes from another module
 * in the same container; here only `presetModule` is loaded, so [testOnlyModule]
 * supplies an in-memory stand-in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PresetBootstrapIntegrationTest {

    private class InMemoryPresetRepository : PresetRepository {
        private val state = MutableStateFlow<FlowPreset?>(null)
        override suspend fun getActivePreset(): FlowPreset? = state.value
        override suspend fun setActivePreset(preset: FlowPreset) { state.value = preset }
        override suspend fun clear() { state.value = null }
        override fun observeActivePreset(): Flow<FlowPreset?> = state.asStateFlow()
    }

    private class AlwaysAvailableGmsPort : GmsAvailabilityPort {
        override suspend fun status(): GmsStatus = GmsStatus.Available
    }

    /** Stands in for the bindings that live outside `presetModule` in production. */
    private val testOnlyModule = module {
        single<PresetRepository> { InMemoryPresetRepository() }
        // TASK-73 — LauncherRoleProvider now depends on GmsAvailabilityPort, bound
        // in production by the (flavor-specific) SetupModule, not presetModule.
        single<GmsAvailabilityPort> { AlwaysAvailableGmsPort() }
    }

    @After fun tearDown() {
        if (GlobalContext.getOrNull() != null) stopKoin()
    }

    @Test
    fun task120Graph_resolvesPresetStack() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        val koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(presetModule, testOnlyModule)
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
            modules(presetModule, testOnlyModule)
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
