package com.launcher.app.preset

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.adapters.preset.PresetReminderService
import com.launcher.adapters.preset.PresetSelectionService
import com.launcher.api.preset.PresetRef
import com.launcher.api.profile.ProfileStore
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.ui.PresetBootRouter
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Spec T67J coverage (SC-007, R4) — measures the boot-time path through
 * [PresetBootRouter] which is the critical-section the SC budget covers:
 * load active profile from DataStore + look up bundled preset via
 * ConfigSource + classify critical-missing settings.
 *
 * Activity start cost (Compose first-frame, Application.onCreate) is
 * unrelated to TASK-65 changes and not measured here.
 *
 * Asserts P95 ≤ 1500ms across 10 iterations. First iteration is treated
 * as warm-up and excluded.
 */
@RunWith(AndroidJUnit4::class)
class BootBenchmarkE2ETest {

    @Before
    fun ensureKoinReady() {
        if (GlobalContext.getOrNull() == null) {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            app.javaClass.getMethod("onCreate").invoke(app)
        }
    }

    @Test
    fun bootRouterP95UnderBudget() = runBlocking {
        val koin = GlobalContext.get()
        val store: ProfileStore = koin.get()
        val configSource: ConfigSource = koin.get()
        val reminders: PresetReminderService = koin.get()
        val selection: PresetSelectionService = koin.get()

        // Set up an active preset so decide() takes the ShowHome branch.
        store.setActive(null)
        selection.beginSetup("simple-launcher")

        val router = PresetBootRouter(store, configSource, reminders)

        val samples = mutableListOf<Long>()
        repeat(10) {
            val ms = measureTimeMillis { runBlocking { router.decide() } }
            samples += ms
        }
        // Drop warm-up sample.
        val trimmed = samples.drop(1).sorted()
        val p95Index = (trimmed.size * 0.95).toInt().coerceAtMost(trimmed.size - 1)
        val p95 = trimmed[p95Index]

        assertNotNull("must have samples", trimmed.firstOrNull())
        assertTrue(
            "PresetBootRouter P95 must be <= 1500ms, got ${p95}ms; samples=$samples",
            p95 <= 1500,
        )
    }
}
