package com.launcher.app.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.wizard.WizardEngine
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: TASK-7 Phase-5 added a second `Map<*, *>` binding to
 * spec015Module which collided with existing Map bindings under JVM type
 * erasure (Koin matches by KClass<Map> only), causing an infinite
 * resolveFromRegistry loop and StackOverflowError on `inject<WizardEngine>()`.
 * Existing Phase-5 unit test bypassed Koin entirely and missed it.
 *
 * Constitution amendment 1.10 (2026-06-25) retired `StepType.Custom` and the
 * associated `CustomStep` / `CustomStepHandler` infrastructure entirely;
 * the named-qualifier fix below remains in place so the moment any future
 * second Map<*, *> binding is added, the graph resolves unambiguously.
 *
 * Uses a bare `android.app.Application` (not LauncherApplication) so we start
 * Koin ourselves with ONLY spec015Module — without the full prod graph
 * (FirebaseFirestore, CryptoModule, etc.) whose deps aren't available in JVM
 * unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class Spec015DiGraphTest {

    @After fun teardown() {
        if (GlobalContext.getOrNull() != null) stopKoin()
    }

    @Test
    fun wizardEngine_resolvesWithoutCircularDependency() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        val koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(spec015Module)
        }.koin
        val engine = koin.get<WizardEngine>()
        check(engine != null)
    }
}
