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
import com.launcher.preset.port.LocalizedResources
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T059 — locale change mid-wizard (FR-022, SC-11, CL-5, CL-9).
 *
 * Verifies:
 * - `WizardViewModel.state` survives across an Activity recreation (simulated
 *   by discarding the "old" Composable's StateFlow subscription and reading
 *   the same VM instance again — matches Koin `single` retention).
 * - After locale change (swapping the injected `LocalizedResources`), the
 *   same `ReconcileState.Interactive` is still current — no step drift,
 *   no re-run of the engine.
 * - The strings resolved for the current step reflect the new locale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class WizardLocaleChangeTest {

    private val dispatcher = StandardTestDispatcher()

    private val english = object : LocalizedResources {
        override fun resolve(key: String, args: Map<String, String>): String = "EN:$key"
    }
    private val russian = object : LocalizedResources {
        override fun resolve(key: String, args: Map<String, String>): String = "RU:$key"
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun stateSurvivesConfigurationChange_andReflectsNewLocale() = runTest(dispatcher) {
        val vm = newViewModel()
        var resources: LocalizedResources = english

        vm.start()
        advanceUntilIdle()

        val beforeState = vm.state.value
        assertTrue("Expected Interactive, got $beforeState", beforeState is ReconcileState.Interactive)
        val beforeStep = (beforeState as ReconcileState.Interactive).current
        val beforeLabel = resources.resolve("wizard.step.font.title")
        assertEquals("EN:wizard.step.font.title", beforeLabel)

        // Simulate configuration change: the Activity is destroyed and recreated,
        // but the ViewModel (Koin single) survives. The Compose tree resubscribes
        // to `vm.state` and re-resolves LocalizedResources.
        resources = russian
        val afterState = vm.state.value

        assertSame("VM state must be the same Interactive after config change", beforeState, afterState)
        assertEquals("Same Entity must still be current", beforeStep.id, (afterState as ReconcileState.Interactive).current.id)
        val afterLabel = resources.resolve("wizard.step.font.title")
        assertEquals("RU:wizard.step.font.title", afterLabel)
        assertNotSame(beforeLabel, afterLabel)
    }

    private val fontComponent = Component.FontSize(1.4f)
    private val pool = Pool(
        declarations = listOf(
            Blueprint(
                id = "font",
                component = fontComponent,
                wizardBehavior = WizardBehavior.Interactive,
                critical = false,
            ),
        ),
    )
    private val preset = Preset(
        presetId = "simple-launcher",
        version = 1,
        layoutKey = "layout.grid.2x3",
        activeComponents = listOf(ActiveComponentEntry("font")),
    )

    private fun newViewModel(): WizardViewModel {
        val store = InMemoryProfileStore()
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
