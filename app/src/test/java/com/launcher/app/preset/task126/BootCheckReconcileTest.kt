package com.launcher.app.preset.task126

import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Profile
import com.launcher.preset.model.ProfileComponent
import com.launcher.preset.model.RunMode
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.port.CapabilityContract
import com.launcher.preset.port.DefaultProviderRegistry
import com.launcher.preset.port.ProfileStore
import com.launcher.preset.port.Provider
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T085 — BootCheck-mode reconcile invokes ONLY `critical=true` providers
 * and skips the rest (FR-012, US-4). Verifies the engine contract that
 * powers `BootCheckWorker.doWork()`.
 */
class BootCheckReconcileTest {

    private val fontComponent = Component.FontSize(1.4f)

    @Test
    fun bootCheck_invokesOnlyCriticalProviders() = runTest {
        val criticalCalls = AtomicInteger(0)
        val nonCriticalCalls = AtomicInteger(0)

        val profile = Profile(
            basedOnPreset = "simple-launcher",
            presetVersion = 1,
            layoutKey = "layout.grid.2x3",
            components = listOf(
                ProfileComponent(
                    id = "role",
                    component = Component.LauncherRole,
                    wizardBehavior = WizardBehavior.AutoApply,
                    critical = true,
                    status = ComponentStatus.Applied,
                ),
                ProfileComponent(
                    id = "font",
                    component = fontComponent,
                    wizardBehavior = WizardBehavior.Interactive,
                    critical = false,
                    status = ComponentStatus.Applied,
                ),
            ),
        )
        val store = InMemoryProfileStore(profile)

        val roleProvider = CountingProvider<Component.LauncherRole>(criticalCalls) {
            Outcome.NeedsApply
        }
        val fontProvider = CountingProvider<Component.FontSize>(nonCriticalCalls) {
            Outcome.Ok
        }

        val handlers = mapOf<HandlerKey, Provider<out Component>>(
            HandlerKey(Component.LauncherRole::class, null, null) to roleProvider,
            HandlerKey(Component.FontSize::class, null, null) to fontProvider,
        )
        val engine = ReconcileEngine(DefaultProviderRegistry(handlers), store)

        engine.run(RunMode.BootCheck)

        // Critical role: check() invoked; check() -> NeedsApply -> apply() invoked
        assertEquals(2, criticalCalls.get()) // check + apply
        // Non-critical font: never touched at all
        assertEquals(0, nonCriticalCalls.get())
    }

    @Test
    fun bootCheck_reappliesCriticalWhenCheckReturnsNeedsApply() = runTest {
        var applyInvocations = 0
        val profile = Profile(
            basedOnPreset = "simple-launcher",
            presetVersion = 1,
            layoutKey = "layout.grid.2x3",
            components = listOf(
                ProfileComponent(
                    id = "role",
                    component = Component.LauncherRole,
                    wizardBehavior = WizardBehavior.AutoApply,
                    critical = true,
                    status = ComponentStatus.Applied,
                ),
            ),
        )
        val store = InMemoryProfileStore(profile)
        val roleProvider = object : Provider<Component.LauncherRole> {
            override suspend fun check(component: Component.LauncherRole, profile: Profile) = Outcome.NeedsApply
            override suspend fun apply(component: Component.LauncherRole, profile: Profile): Outcome {
                applyInvocations++
                return Outcome.Ok
            }
        }
        val handlers = mapOf<HandlerKey, Provider<out Component>>(
            HandlerKey(Component.LauncherRole::class, null, null) to roleProvider,
        )
        val engine = ReconcileEngine(DefaultProviderRegistry(handlers), store)

        engine.run(RunMode.BootCheck)

        assertEquals(1, applyInvocations)
        val persisted = store.load()!!
        assertEquals(ComponentStatus.Applied, persisted.components.first { it.id == "role" }.status)
    }

    private class CountingProvider<T : Component>(
        private val counter: AtomicInteger,
        private val checkResult: () -> Outcome,
    ) : Provider<T> {
        override suspend fun check(component: T, profile: Profile): Outcome {
            counter.incrementAndGet()
            return checkResult()
        }
        override suspend fun apply(component: T, profile: Profile): Outcome {
            counter.incrementAndGet()
            return Outcome.Ok
        }
    }

    private class InMemoryProfileStore(initial: Profile?) : ProfileStore {
        private val state = MutableStateFlow(initial)
        override fun observe(): Flow<Profile?> = state.asStateFlow()
        override suspend fun load(): Profile? = state.value
        override suspend fun save(profile: Profile) { state.value = profile }
        override suspend fun setPreWizardSnapshot(snapshot: Profile) {
            state.value = state.value?.copy(preWizardSnapshot = snapshot.copy(preWizardSnapshot = null))
        }
        override suspend fun restoreFromPreWizardSnapshot(): Profile? {
            val snap = state.value?.preWizardSnapshot ?: return state.value
            state.value = snap
            return snap
        }
    }
}
