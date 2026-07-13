package com.launcher.app.preset

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.app.preset.task120.PresetBootstrap
import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.model.RunMode
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * TASK-126 Phase 5 T091 — boot-time benchmark for the new ECS runtime.
 * Replaces the legacy `PresetBootRouter.decide()` measurement with the
 * actual critical section that runs on cold boot:
 *
 *   PresetBootstrap.bootstrap()  (idempotent — no-op if profile exists)
 *   + ReconcileEngine.run(RunMode.BootCheck)  (only critical=true providers)
 *
 * Asserts P95 ≤ 1500 ms across 10 iterations. First iteration is warm-up.
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
    fun bootCheckP95UnderBudget() = runBlocking {
        val koin = GlobalContext.get()
        val bootstrap: PresetBootstrap = koin.get()
        val engine: ReconcileEngine = koin.get()

        // Warm the profile so subsequent iterations measure the boot-check
        // steady state (bootstrap is no-op when a profile already exists).
        bootstrap.bootstrap()

        val samples = mutableListOf<Long>()
        repeat(10) {
            val ms = measureTimeMillis {
                runBlocking {
                    bootstrap.bootstrap()
                    engine.run(RunMode.BootCheck)
                }
            }
            samples += ms
        }
        val trimmed = samples.drop(1).sorted()
        val p95Index = (trimmed.size * 0.95).toInt().coerceAtMost(trimmed.size - 1)
        val p95 = trimmed[p95Index]

        assertNotNull("must have samples", trimmed.firstOrNull())
        assertTrue(
            "BootCheck P95 must be <= 1500ms, got ${p95}ms; samples=$samples",
            p95 <= 1500,
        )
    }
}
