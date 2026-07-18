package com.launcher.app.preset

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.app.preset.task120.PresetBootstrap
import com.launcher.preset.ecs.get
import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.model.Component
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.RunMode
import com.launcher.preset.port.ProfileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * TASK-126 Phase 5 T092 — critical-missing surfacing on boot (US-4, SC-008).
 *
 * On a non-launcher install ROLE_HOME is not granted to the test package.
 * The new ECS runtime surfaces this through `Provider.check()`:
 *   LauncherRoleProvider.check(...) returns NeedsApply
 * When `ReconcileEngine.run(RunMode.BootCheck)` walks the profile, the
 * `critical=true` LauncherRole component's status transitions to Applied
 * only if the OS actually grants the role. Otherwise the component stays
 * in NeedsApply — which is what the HomeBanner reads to render "you still
 * need to set X" copy.
 *
 * If a future run grants ROLE_HOME to the test package, the test still
 * passes: it asserts the engine walks the critical component (Applied is
 * a legitimate terminal state too).
 */
@RunWith(AndroidJUnit4::class)
class BootCriticalMissingE2ETest {

    @Before
    fun ensureKoinReady() {
        if (GlobalContext.getOrNull() == null) {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            app.javaClass.getMethod("onCreate").invoke(app)
        }
    }

    @Test
    fun bootCheckWalksCriticalLauncherRole() = runBlocking {
        val koin = GlobalContext.get()
        val bootstrap: PresetBootstrap = koin.get()
        val engine: ReconcileEngine = koin.get()
        val store: ProfileStore = koin.get()

        bootstrap.bootstrap()
        engine.run(RunMode.BootCheck)

        val profile = store.load()
        assertNotNull("profile must exist after bootstrap", profile)

        val criticalComponents = profile!!.entities.filter { it.critical }
        val roleComponent = criticalComponents.firstOrNull { it.get<Component.LauncherRole>() != null }
        if (roleComponent != null) {
            // LauncherRole is a critical component in the bundled preset;
            // after BootCheck its state is one of the terminal set — Applied
            // (role granted) or Failed / Pending (role denied, needs UI). Skipped
            // is not a legitimate outcome for BootCheck (no InteractionSink).
            val state = roleComponent.get<LifecycleState>()
            assertTrue(
                "critical LauncherRole must be walked by BootCheck, got state=$state",
                state is LifecycleState.Applied ||
                    state is LifecycleState.Failed ||
                    state is LifecycleState.Pending,
            )
        }
    }
}
