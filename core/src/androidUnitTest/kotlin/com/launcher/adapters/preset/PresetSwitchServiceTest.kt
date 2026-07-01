package com.launcher.adapters.preset

import com.launcher.api.preset.AbstractProfile
import com.launcher.api.preset.Config
import com.launcher.api.preset.Criticality
import com.launcher.api.preset.PRESET_SCHEMA_VERSION
import com.launcher.api.preset.Preset
import com.launcher.api.preset.PresetRef
import com.launcher.api.preset.ref
import com.launcher.api.profile.Grid
import com.launcher.api.profile.Layout
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.ProfileStore
import com.launcher.api.profile.ProfileStoreState
import com.launcher.api.profile.Screen
import com.launcher.api.switchstrategy.CopyOnActivateStrategy
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.ConfigSummary
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.ConfigDocumentHeader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetSwitchServiceTest {

    private fun preset(uid: String, version: Int = 1): Preset = Preset(
        schemaVersion = PRESET_SCHEMA_VERSION,
        uid = uid,
        version = version,
        slug = uid.substringAfterLast('.'),
        label = "$uid.label",
        description = "$uid.desc",
        configs = listOf(
            Config(
                id = "ui.font.large",
                poolId = "ui-customization",
                poolVersion = 1,
                entryId = "ui.font.large",
                title = "t",
                description = "d",
                check = CheckSpec.UIFont(1.3f),
                apply = ApplySpec.InAppOnly,
                criticality = Criticality.Required,
            ),
        ),
        abstractProfile = AbstractProfile(layout = Layout.empty(), bindings = emptyList()),
    )

    private class FakeConfigSource(private val presets: Map<String, Preset>) : ConfigSource {
        override suspend fun list(kind: ConfigKind): List<ConfigSummary> =
            presets.values.map { ConfigSummary(it.slug, it.label, it.description, emptyList()) }

        override suspend fun load(kind: ConfigKind, id: String): ConfigSourceResult {
            val p = presets[id] ?: return ConfigSourceResult.NotFound(id)
            return ConfigSourceResult.Success(
                ConfigDocument.PresetDoc(
                    header = ConfigDocumentHeader(p.schemaVersion, p.slug, p.label, p.description, emptyList()),
                    preset = p,
                ),
            )
        }
    }

    private class InMemoryProfileStore : ProfileStore {
        private var state = ProfileStoreState()
        override suspend fun load(): ProfileStoreState = state
        override suspend fun save(state: ProfileStoreState) { this.state = state }
        override suspend fun getActive(): Pair<PresetRef, ProfileData>? {
            val ref = state.activePresetRef ?: return null
            val data = state.profiles[ref.toCompositeKey()] ?: return null
            return ref to data
        }
        override suspend fun putProfile(ref: PresetRef, data: ProfileData) {
            state = state.copy(profiles = state.profiles + (ref.toCompositeKey() to data))
        }
        override suspend fun setActive(ref: PresetRef?) {
            state = state.copy(activePresetRef = ref)
        }
    }

    @Test
    fun switchToFreshTargetUsesCopyOnActivate() = runTest {
        val targetPreset = preset("com.launcher.preset.workspace")
        val service = PresetSwitchService(
            configSource = FakeConfigSource(mapOf(targetPreset.slug to targetPreset)),
            profileStore = InMemoryProfileStore(),
            switchStrategy = CopyOnActivateStrategy(),
        )
        val outcome = service.switchTo(targetPreset.slug)
        assertTrue(outcome is PresetSwitchService.SwitchOutcome.Switched)
        val s = outcome as PresetSwitchService.SwitchOutcome.Switched
        assertEquals(false, s.restored)
        assertEquals(targetPreset.ref, s.preset.ref)
    }

    @Test
    fun switchBackRestoresExistingProfileData() = runTest {
        val a = preset("com.launcher.preset.simple-launcher")
        val b = preset("com.launcher.preset.workspace")
        val store = InMemoryProfileStore()
        val service = PresetSwitchService(
            configSource = FakeConfigSource(mapOf(a.slug to a, b.slug to b)),
            profileStore = store,
            switchStrategy = CopyOnActivateStrategy(),
        )
        // Seed a custom ProfileData for preset A so we can detect it's restored.
        val customLayout = Layout(listOf(Screen("seeded", Grid(rows = 5, cols = 5))))
        store.putProfile(a.ref, ProfileData(layout = customLayout))
        store.setActive(a.ref)
        // Go to B (fresh), then back to A — A's seeded layout must come back.
        service.switchTo(b.slug)
        val back = service.switchTo(a.slug)
        val s = back as PresetSwitchService.SwitchOutcome.Switched
        assertEquals(true, s.restored)
        assertEquals(customLayout, s.profile.layout)
    }

    @Test
    fun unknownSlugReportsNotFound() = runTest {
        val service = PresetSwitchService(
            configSource = FakeConfigSource(emptyMap()),
            profileStore = InMemoryProfileStore(),
            switchStrategy = CopyOnActivateStrategy(),
        )
        val outcome = service.switchTo("does-not-exist")
        assertTrue(outcome is PresetSwitchService.SwitchOutcome.PresetNotFound)
    }
}
