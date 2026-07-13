package com.launcher.app.preset.task126

import com.launcher.app.preset.task120.PresetBootstrap
import com.launcher.app.wizard.WizardViewModel
import com.launcher.preset.engine.PresetValidator
import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.engine.ReconcileState
import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentDeclaration
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Profile
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.port.CapabilityContract
import com.launcher.preset.port.DefaultProviderRegistry
import com.launcher.preset.port.PoolSource
import com.launcher.preset.port.PresetSource
import com.launcher.preset.port.ProfileStore
import com.launcher.preset.port.Provider
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T061 — Denial UX (FR-002, FR-006 CL-6).
 *
 * Verifies:
 * - `required=false` denial → step Skipped, wizard proceeds to the next step
 *   without transitioning to Denied.
 * - `required=true` denial → blocking `ReconcileState.Denied(required=true)` — UI
 *   renders «try preset Y where it is not needed» copy (copy verified in
 *   `WizardScreen` layer; this test guards the state-machine contract).
 * - After a critical denial, the persisted Profile's Applied components are
 *   preserved (denial does not roll back prior progress).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class WizardDenialUxTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val fontComponent = Component.FontSize(1.4f)

    @Test
    fun nonCriticalDenial_marksSkipped_andProceeds() = runTest(dispatcher) {
        val store = InMemoryProfileStore()
        val pool = Pool(
            declarations = listOf(
                ComponentDeclaration("font", fontComponent, WizardBehavior.Interactive, critical = false),
                ComponentDeclaration("role", Component.LauncherRole, WizardBehavior.Interactive, critical = false),
            ),
        )
        val preset = Preset(
            presetId = "simple-launcher",
            version = 1,
            layoutKey = "layout.grid.2x3",
            activeComponents = listOf(ActiveComponentEntry("font"), ActiveComponentEntry("role")),
        )
        val vm = newViewModel(store, pool, preset)

        vm.start(); advanceUntilIdle()
        val first = vm.state.value as ReconcileState.Interactive
        assertEquals("font", first.current.id)
        vm.deny(first.current); advanceUntilIdle()

        val next = vm.state.value
        assertTrue("Expected next Interactive, got $next", next is ReconcileState.Interactive)
        assertEquals("role", (next as ReconcileState.Interactive).current.id)

        // Persisted status of the skipped step
        val persisted = store.load()!!
        assertEquals(ComponentStatus.Skipped, persisted.components.first { it.id == "font" }.status)
    }

    @Test
    fun criticalDenial_emitsDenied_andPreservesPriorProgress() = runTest(dispatcher) {
        val store = InMemoryProfileStore()
        val pool = Pool(
            declarations = listOf(
                ComponentDeclaration("font", fontComponent, WizardBehavior.Interactive, critical = false),
                ComponentDeclaration("role", Component.LauncherRole, WizardBehavior.Interactive, critical = true),
            ),
        )
        val preset = Preset(
            presetId = "simple-launcher",
            version = 1,
            layoutKey = "layout.grid.2x3",
            activeComponents = listOf(ActiveComponentEntry("font"), ActiveComponentEntry("role")),
        )
        val vm = newViewModel(store, pool, preset)

        vm.start(); advanceUntilIdle()
        vm.respond(fontComponent); advanceUntilIdle()

        val roleStep = vm.state.value as ReconcileState.Interactive
        assertEquals("role", roleStep.current.id)
        vm.deny(roleStep.current); advanceUntilIdle()

        val denied = vm.state.value
        assertTrue("Expected Denied, got $denied", denied is ReconcileState.Denied)
        assertTrue((denied as ReconcileState.Denied).required)
        assertEquals("role", denied.component.id)

        // Prior font step remains Applied.
        val persisted = store.load()!!
        assertEquals(ComponentStatus.Applied, persisted.components.first { it.id == "font" }.status)
    }

    private fun newViewModel(store: ProfileStore, pool: Pool, preset: Preset): WizardViewModel {
        val bootstrap = PresetBootstrap(
            poolSource = StubPoolSource(pool),
            presetSource = StubPresetSource(mapOf(preset.presetId to preset)),
            validator = PresetValidator(EmptyContract),
            factory = ProfileFactory(),
            store = store,
            defaultPresetId = preset.presetId,
        )
        val handlers = mapOf<HandlerKey, Provider<out Component>>(
            HandlerKey(Component.FontSize::class, null, null) to AutoOkProvider<Component.FontSize>(),
            HandlerKey(Component.LauncherRole::class, null, null) to AutoOkProvider<Component.LauncherRole>(),
        )
        val engine = ReconcileEngine(DefaultProviderRegistry(handlers), store)
        return WizardViewModel(bootstrap, engine, store)
    }

    private class StubPoolSource(private val pool: Pool) : PoolSource {
        override suspend fun loadPool() = pool
    }
    private class StubPresetSource(private val map: Map<String, Preset>) : PresetSource {
        override suspend fun loadPreset(presetId: String) = map[presetId]
        override suspend fun listAvailable() = map.keys.toList()
    }
    private class InMemoryProfileStore(initial: Profile? = null) : ProfileStore {
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
    private class AutoOkProvider<T : Component> : Provider<T> {
        override suspend fun check(component: T, profile: Profile) = Outcome.NeedsApply
        override suspend fun apply(component: T, profile: Profile) = Outcome.Ok
    }
    private object EmptyContract : CapabilityContract {
        override fun requires(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
        override fun provides(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
    }
}
