package com.launcher.app.settings

import com.launcher.api.localization.StringResolver
import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.Component
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Entity
import com.launcher.preset.model.SettingsMapEntry
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.port.PresetSource
import com.launcher.preset.port.ProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T073 — PendingChecklistViewModel drives Settings' «what still needs
 * your attention» surface off the new preset runtime (FR-009, US-5).
 *
 * Verifies:
 * - Loading from ProfileStore + PresetSource replaces the legacy
 *   ConfigKind.WizardManifest lookup end-to-end (no `ConfigSource` in the
 *   test doubles).
 * - Only Interactive components with `status != Applied` surface.
 * - `isRequired` mirrors `Entity.critical`.
 * - Missing settingsMap entry → fall back to component id as labelKey.
 * - Empty profile / missing preset → empty list, no crash.
 */
class PendingChecklistViewModelTest {

    private val fontComponent = Component.FontSize(1.4f)
    private val roleComponent = Component.LauncherRole

    private val preset = Preset(
        presetId = "simple-launcher",
        version = 1,
        layoutKey = "layout.grid.2x3",
        activeComponents = listOf(
            ActiveComponentEntry("font"),
            ActiveComponentEntry("role"),
            ActiveComponentEntry("silent"),
        ),
        settingsMap = listOf(
            SettingsMapEntry(poolRef = "font", categoryKey = "settings_font_label"),
            SettingsMapEntry(poolRef = "role", categoryKey = "settings_role_label"),
            // Note: "silent" has no settingsMap entry — should fall back to id.
        ),
    )

    private val fullProfile = Profile(
        basedOnPreset = "simple-launcher",
        presetVersion = 1,
        layoutKey = "layout.grid.2x3",
        entities = listOf(
            Entity(
                id = "font",
                components = listOf(fontComponent, LifecycleState.Pending),
                wizardBehavior = WizardBehavior.Interactive,
                critical = false,
            ),
            Entity(
                id = "role",
                components = listOf(roleComponent, LifecycleState.Failed(FailReason.Cancelled)),
                wizardBehavior = WizardBehavior.Interactive,
                critical = true,
            ),
            Entity(
                id = "silent",
                components = listOf(fontComponent, LifecycleState.Pending),
                wizardBehavior = WizardBehavior.AutoApply,
                critical = false,
            ),
            Entity(
                id = "already-done",
                components = listOf(fontComponent, LifecycleState.Applied),
                wizardBehavior = WizardBehavior.Interactive,
                critical = false,
            ),
        ),
    )

    private val stringResolver = object : StringResolver {
        override fun resolve(key: String, args: Map<String, Any>): String = key
        override fun resolvePlural(key: String, count: Int, args: Map<String, Any>): String = key
        override fun currentLocaleTag(): String = "en"
    }

    @Test
    fun load_returnsInteractiveNonAppliedComponents_withMappedLabels() = runTest {
        val vm = PendingChecklistViewModel(
            profileStore = InMemoryProfileStore(fullProfile),
            presetSource = StubPresetSource(mapOf(preset.presetId to preset)),
            stringResolver = stringResolver,
        )

        val state = vm.load()

        assertEquals(2, state.items.size)
        val font = state.items.first { it.refId == "font" }
        assertEquals("settings_font_label", font.labelKey)
        assertEquals(false, font.isRequired)
        assertEquals(LifecycleState.Pending, font.state)

        val role = state.items.first { it.refId == "role" }
        assertEquals("settings_role_label", role.labelKey)
        assertTrue(role.isRequired)
        assertTrue(role.state is LifecycleState.Failed)

        // AutoApply "silent" and Applied "already-done" must NOT surface.
        assertTrue(state.items.none { it.refId == "silent" })
        assertTrue(state.items.none { it.refId == "already-done" })
    }

    @Test
    fun load_fallsBackToComponentId_whenSettingsMapMissing() = runTest {
        val profile = fullProfile.copy(
            entities = listOf(
                Entity(
                    id = "orphan",
                    components = listOf(fontComponent, LifecycleState.Pending),
                    wizardBehavior = WizardBehavior.Interactive,
                    critical = false,
                ),
            ),
        )
        val vm = PendingChecklistViewModel(
            profileStore = InMemoryProfileStore(profile),
            presetSource = StubPresetSource(mapOf(preset.presetId to preset)),
            stringResolver = stringResolver,
        )

        val state = vm.load()
        assertEquals(1, state.items.size)
        assertEquals("orphan", state.items.single().labelKey)
    }

    @Test
    fun load_emptyProfile_returnsEmptyList() = runTest {
        val vm = PendingChecklistViewModel(
            profileStore = InMemoryProfileStore(null),
            presetSource = StubPresetSource(emptyMap()),
            stringResolver = stringResolver,
        )
        assertTrue(vm.load().items.isEmpty())
    }

    @Test
    fun load_missingPresetSource_treatsSettingsMapAsEmpty() = runTest {
        val vm = PendingChecklistViewModel(
            profileStore = InMemoryProfileStore(fullProfile),
            presetSource = StubPresetSource(emptyMap()),
            stringResolver = stringResolver,
        )
        val state = vm.load()
        assertEquals(2, state.items.size)
        // All fall back to raw id.
        assertTrue(state.items.all { it.refId == it.labelKey })
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

    private class StubPresetSource(private val map: Map<String, Preset>) : PresetSource {
        override suspend fun loadPreset(presetId: String) = map[presetId]
        override suspend fun listAvailable() = map.keys.toList()
    }
}
