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
import com.launcher.preset.model.Blueprint
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T057 — WizardViewModel Robolectric coverage (FR-008).
 *
 * Verifies:
 * - state transitions Idle → Loading → Interactive → Done
 * - `respond()` advances the engine to the next component or terminal state
 * - non-critical `deny()` marks the component Skipped and proceeds
 * - critical `deny()` transitions to Denied and does NOT advance
 * - `reset()` returns to Idle so `start()` can be re-invoked
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class WizardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val fontComponent = Component.FontSize(1.4f)
    private val themeComponent = Component.Theme(
        paletteSeedHex = "#0B6BCB",
        typographyScale = com.launcher.preset.model.TypographyScale.Medium,
        shapeStyle = com.launcher.preset.model.ShapeStyle.Rounded,
        darkMode = false,
    )

    private val pool = Pool(
        declarations = listOf(
            Blueprint(
                id = "font",
                component = fontComponent,
                wizardBehavior = WizardBehavior.Interactive,
                critical = false,
            ),
            Blueprint(
                id = "theme",
                component = themeComponent,
                wizardBehavior = WizardBehavior.Interactive,
                critical = false,
            ),
        ),
    )

    private val preset = Preset(
        presetId = "simple-launcher",
        version = 1,
        layoutKey = "layout.grid.2x3",
        activeComponents = listOf(
            ActiveComponentEntry("font"),
            ActiveComponentEntry("theme"),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun start_walksInteractiveComponents_untilDone() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.start()
        advanceUntilIdle()

        val first = vm.state.value
        assertTrue("Expected Interactive, got $first", first is ReconcileState.Interactive)
        assertEquals("font", (first as ReconcileState.Interactive).current.id)

        vm.respond(fontComponent)
        advanceUntilIdle()

        val second = vm.state.value
        assertTrue("Expected Interactive, got $second", second is ReconcileState.Interactive)
        assertEquals("theme", (second as ReconcileState.Interactive).current.id)

        vm.respond(themeComponent)
        advanceUntilIdle()

        val terminal = vm.state.value
        assertTrue("Expected Done, got $terminal", terminal is ReconcileState.Done)
    }

    @Test
    fun deny_nonCritical_marksSkipped_andProceeds() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.start()
        advanceUntilIdle()

        val step = vm.state.value as ReconcileState.Interactive
        vm.deny(step.current)
        advanceUntilIdle()

        val next = vm.state.value
        assertTrue("Expected next Interactive, got $next", next is ReconcileState.Interactive)
        assertEquals("theme", (next as ReconcileState.Interactive).current.id)
    }

    @Test
    fun deny_critical_transitionsToDenied_andHalts() = runTest(dispatcher) {
        val criticalPool = Pool(
            declarations = listOf(
                Blueprint(
                    id = "role",
                    component = Component.LauncherRole,
                    wizardBehavior = WizardBehavior.Interactive,
                    critical = true,
                ),
            ),
        )
        val criticalPreset = preset.copy(activeComponents = listOf(ActiveComponentEntry("role")))
        val vm = newViewModel(pool = criticalPool, preset = criticalPreset)
        vm.start()
        advanceUntilIdle()

        val step = vm.state.value as ReconcileState.Interactive
        vm.deny(step.current)
        advanceUntilIdle()

        val denied = vm.state.value
        assertTrue("Expected Denied, got $denied", denied is ReconcileState.Denied)
        assertTrue("Required flag must be true", (denied as ReconcileState.Denied).required)
    }

    @Test
    fun reset_returnsToIdle_andAllowsRestart() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.start()
        advanceUntilIdle()
        assertTrue(vm.state.value is ReconcileState.Interactive)

        vm.reset()
        assertEquals(ReconcileState.Idle, vm.state.value)

        vm.start()
        advanceUntilIdle()
        assertTrue(vm.state.value is ReconcileState.Interactive)
    }

    @Test
    fun start_isNoOp_whenAlreadyRunning() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.start()
        advanceUntilIdle()
        val first = vm.state.value
        vm.start()
        advanceUntilIdle()
        assertEquals(first, vm.state.value)
    }

    // Helpers -------------------------------------------------------------

    private fun newViewModel(pool: Pool = this.pool, preset: Preset = this.preset): WizardViewModel {
        val store = InMemoryProfileStore()
        val validator = PresetValidator(EmptyCapabilityContract)
        val factory = ProfileFactory()
        val bootstrap = PresetBootstrap(
            poolSource = StubPoolSource(pool),
            presetSource = StubPresetSource(mapOf(preset.presetId to preset)),
            validator = validator,
            factory = factory,
            store = store,
            defaultPresetId = preset.presetId,
        )
        val handlers = mapOf<HandlerKey, Provider<out Component>>(
            HandlerKey(Component.FontSize::class, null, null) to AutoOkProvider<Component.FontSize>(),
            HandlerKey(Component.Theme::class, null, null) to AutoOkProvider<Component.Theme>(),
            HandlerKey(Component.LauncherRole::class, null, null) to AutoOkProvider<Component.LauncherRole>(),
        )
        val registry = DefaultProviderRegistry(handlers)
        val engine = ReconcileEngine(registry, store)
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
        override suspend fun check(component: T, profile: Profile): Outcome = Outcome.NeedsApply
        override suspend fun apply(component: T, profile: Profile): Outcome = Outcome.Ok
    }

    private object EmptyCapabilityContract : CapabilityContract {
        override fun requires(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
        override fun provides(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
    }
}
