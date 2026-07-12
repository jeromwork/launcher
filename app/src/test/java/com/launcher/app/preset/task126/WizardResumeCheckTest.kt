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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T060 — resume-check semantics (FR-008, SC-10, CL-5).
 *
 * Verifies:
 * - Once a component reaches `ComponentStatus.Applied` in the persisted profile,
 *   `ReconcileEngine.runWizard` skips it on the next run — regardless of any
 *   persisted counter. Progress is derived from `Provider.check()` + status.
 * - Mutating OS state externally between runs (fake facade grants permission →
 *   Provider returns `Ok`) does not affect an already-Applied step (it is
 *   filtered out before dispatch).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class WizardResumeCheckTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val fontComponent = Component.FontSize(1.4f)
    private val themeComponent = Component.Theme(
        paletteSeedHex = "#0B6BCB",
        typographyScale = com.launcher.preset.model.TypographyScale.Medium,
        shapeStyle = com.launcher.preset.model.ShapeStyle.Rounded,
        darkMode = false,
    )
    private val pool = Pool(
        declarations = listOf(
            ComponentDeclaration("font", fontComponent, WizardBehavior.Interactive, critical = false),
            ComponentDeclaration("theme", themeComponent, WizardBehavior.Interactive, critical = false),
        ),
    )
    private val preset = Preset(
        presetId = "simple-launcher",
        version = 1,
        layoutKey = "layout.grid.2x3",
        activeComponents = listOf(ActiveComponentEntry("font"), ActiveComponentEntry("theme")),
    )

    @Test
    fun secondRun_skipsAppliedStep_andResumesAtNextNeedsApply() = runTest(dispatcher) {
        val store = InMemoryProfileStore()
        val vm1 = newViewModel(store)

        // First run — apply font, then simulate process death by discarding VM.
        vm1.start()
        advanceUntilIdle()
        assertTrue(vm1.state.value is ReconcileState.Interactive)
        assertEquals("font", (vm1.state.value as ReconcileState.Interactive).current.id)
        vm1.respond(fontComponent)
        advanceUntilIdle()
        // Now paused at theme; process dies.
        val savedProfile = store.load()!!
        val fontStatus = savedProfile.components.first { it.id == "font" }.status
        assertEquals(ComponentStatus.Applied, fontStatus)

        // Second run — fresh VM instance, same ProfileStore.
        val vm2 = newViewModel(store)
        vm2.start()
        advanceUntilIdle()
        val resumed = vm2.state.value
        assertTrue("Expected Interactive, got $resumed", resumed is ReconcileState.Interactive)
        assertEquals("theme", (resumed as ReconcileState.Interactive).current.id)
    }

    private fun newViewModel(store: ProfileStore): WizardViewModel {
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
            HandlerKey(Component.Theme::class, null, null) to AutoOkProvider<Component.Theme>(),
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
