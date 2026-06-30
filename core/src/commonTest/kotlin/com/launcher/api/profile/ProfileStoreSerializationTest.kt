package com.launcher.api.profile

import com.launcher.api.preset.Config
import com.launcher.api.preset.Criticality
import com.launcher.api.preset.PresetRef
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ProfileStoreSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun roundtripsStateWithMultiplePresetsAndAllAppliedStateVariants() {
        val refA = PresetRef("com.launcher.preset.simple-launcher", 1)
        val refB = PresetRef("com.launcher.preset.workspace", 2)

        val cfg = Config(
            id = "ui.font.large",
            poolId = "ui-customization",
            poolVersion = 1,
            entryId = "ui.font.large",
            title = "t",
            description = "d",
            check = CheckSpec.UIFont(minScale = 1.3f),
            apply = ApplySpec.InAppOnly,
            criticality = Criticality.Optional,
        )

        val state = ProfileStoreState(
            activePresetRef = refA,
            profiles = mapOf(
                refA.toCompositeKey() to ProfileData(
                    layout = Layout.empty(),
                    settings = listOf(
                        SettingEntry(cfg, AppliedState.NotApplied),
                        SettingEntry(cfg.copy(id = "a"), AppliedState.Applied),
                        SettingEntry(cfg.copy(id = "b"), AppliedState.WithValue("1.5")),
                        SettingEntry(cfg.copy(id = "c"), AppliedState.Indeterminate),
                    ),
                ),
                refB.toCompositeKey() to ProfileData(layout = Layout.empty()),
            ),
        )

        val encoded = json.encodeToString(ProfileStoreState.serializer(), state)
        val decoded = json.decodeFromString(ProfileStoreState.serializer(), encoded)
        assertEquals(state, decoded)
        // Composite key format guarantee
        assertEquals("com.launcher.preset.simple-launcher::1", refA.toCompositeKey())
        assertEquals("com.launcher.preset.workspace::2", refB.toCompositeKey())
    }
}
