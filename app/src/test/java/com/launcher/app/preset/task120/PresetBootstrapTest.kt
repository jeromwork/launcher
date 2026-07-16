package com.launcher.app.preset.task120

import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.preset.engine.PresetValidator
import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.model.Blueprint
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Profile
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.port.CapabilityContract
import com.launcher.preset.port.PoolSource
import com.launcher.preset.port.PresetSource
import com.launcher.preset.port.ProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class PresetBootstrapTest {

    private val pool = Pool(
        declarations = listOf(
            Blueprint(
                id = "font-tile",
                component = Component.FontSize(1.6f),
                wizardBehavior = WizardBehavior.Interactive,
                critical = false,
            ),
        ),
    )
    private val preset = Preset(
        presetId = "simple-launcher",
        version = 1,
        layoutKey = "layout.grid.2x3",
        activeComponents = listOf(ActiveComponentEntry("font-tile")),
    )

    private val emptyContract = object : CapabilityContract {
        override fun requires(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
        override fun provides(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
    }

    @Test
    fun bootstrap_activatesBundledPreset_whenNoProfileExists() = runTest {
        val store = InMemoryProfileStore()
        val boot = PresetBootstrap(
            StubPoolSource(pool), StubPresetSource(mapOf("simple-launcher" to preset)),
            PresetValidator(emptyContract), ProfileFactory(), store,
        )
        val outcome = boot.bootstrap()
        assertTrue("Expected Activated, got $outcome", outcome is PresetBootstrap.BootstrapOutcome.Activated)
        assertEquals("simple-launcher", (outcome as PresetBootstrap.BootstrapOutcome.Activated).presetId)
        assertNotNull(store.load())
    }

    @Test
    fun bootstrap_alreadyActive_whenProfileExists() = runTest {
        val existing = ProfileFactory().create(preset, pool)
        val store = InMemoryProfileStore(existing)
        val boot = PresetBootstrap(
            StubPoolSource(pool), StubPresetSource(mapOf("simple-launcher" to preset)),
            PresetValidator(emptyContract), ProfileFactory(), store,
        )
        assertTrue(boot.bootstrap() is PresetBootstrap.BootstrapOutcome.AlreadyActive)
    }

    // ---- TASK-127 AC #8: the picker's choice must actually reach the profile ----

    private val launcherPreset = Preset(
        presetId = "launcher",
        version = 1,
        layoutKey = "layout.grid.2x3",
        activeComponents = listOf(ActiveComponentEntry("font-tile")),
    )

    private val bothPresets = StubPresetSource(
        mapOf("simple-launcher" to preset, "launcher" to launcherPreset),
    )

    private class FakePresetRepository(private var active: FlowPreset?) : PresetRepository {
        override suspend fun getActivePreset(): FlowPreset? = active
        override suspend fun setActivePreset(preset: FlowPreset) { active = preset }
        override suspend fun clear() { active = null }
        override fun observeActivePreset(): kotlinx.coroutines.flow.Flow<FlowPreset?> =
            MutableStateFlow(active)
    }

    @Test
    fun bootstrap_activatesThePresetTheUserPicked_notTheDefault() = runTest {
        // The regression this closes: picking "Лаунчер" still produced the
        // simple-launcher profile, so the picker had no visible effect.
        val store = InMemoryProfileStore()
        val boot = PresetBootstrap(
            StubPoolSource(pool), bothPresets,
            PresetValidator(emptyContract), ProfileFactory(), store,
            presetRepository = FakePresetRepository(FlowPreset.LAUNCHER),
        )

        val outcome = boot.bootstrap()

        assertEquals("launcher", (outcome as PresetBootstrap.BootstrapOutcome.Activated).presetId)
        assertEquals("launcher", store.load()?.basedOnPreset)
    }

    @Test
    fun bootstrap_fallsBackToDefault_whenNothingPickedYet() = runTest {
        val store = InMemoryProfileStore()
        val boot = PresetBootstrap(
            StubPoolSource(pool), bothPresets,
            PresetValidator(emptyContract), ProfileFactory(), store,
            presetRepository = FakePresetRepository(null),
        )

        val outcome = boot.bootstrap()

        assertEquals("simple-launcher", (outcome as PresetBootstrap.BootstrapOutcome.Activated).presetId)
    }

    @Test
    fun bootstrap_honoursSimpleLauncherPick_explicitly() = runTest {
        val store = InMemoryProfileStore()
        val boot = PresetBootstrap(
            StubPoolSource(pool), bothPresets,
            PresetValidator(emptyContract), ProfileFactory(), store,
            presetRepository = FakePresetRepository(FlowPreset.SIMPLE_LAUNCHER),
        )

        assertEquals(
            "simple-launcher",
            (boot.bootstrap() as PresetBootstrap.BootstrapOutcome.Activated).presetId,
        )
    }

    @Test
    fun bootstrap_presetNotFound_whenPresetSourceReturnsNull() = runTest {
        val boot = PresetBootstrap(
            StubPoolSource(pool), StubPresetSource(emptyMap()),
            PresetValidator(emptyContract), ProfileFactory(), InMemoryProfileStore(),
            defaultPresetId = "missing",
        )
        assertTrue(boot.bootstrap() is PresetBootstrap.BootstrapOutcome.PresetNotFound)
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
        override suspend fun load() = state.value
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
