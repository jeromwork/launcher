package com.launcher.app.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.wizard.WizardEngine
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: TASK-7 Phase-5 added `StepType.Custom(CUSTOM_DISPATCH_KEY)` to
 * the WizardStep map, after which `WizardActivity` crashes on real devices
 * with a Koin circular-dependency StackOverflowError during
 * `inject<WizardEngine>()`. Existing PairAdminStepIntegrationTest bypassed
 * Koin (instantiated CustomStep by hand) and missed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class Spec015DiGraphTest {

    @After fun teardown() = stopKoin()

    @Test
    fun wizardEngine_resolvesWithoutCircularDependency() {
        val koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(spec015Module)
        }.koin
        val engine = koin.get<WizardEngine>()
        check(engine != null)
    }
}
