package com.launcher.app.preset.task120

import com.launcher.preset.engine.PresetValidator
import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentDeclaration
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
            ComponentDeclaration(
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
