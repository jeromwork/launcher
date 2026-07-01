package com.launcher.api.preset

import com.launcher.api.profile.Binding
import com.launcher.api.profile.Grid
import com.launcher.api.profile.Layout
import com.launcher.api.profile.Screen
import com.launcher.api.profile.Slot
import com.launcher.api.profile.SlotPosition
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class PresetWireFormatRoundtripTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun roundtripsSelfContainedPresetWithMultipleConfigsAndAbstractProfile() {
        val original = Preset(
            schemaVersion = PRESET_SCHEMA_VERSION,
            uid = "com.launcher.preset.test",
            version = 2,
            slug = "test-preset",
            label = "Test preset",
            description = "Roundtrip fixture",
            configs = listOf(
                Config(
                    id = "android.role.home",
                    poolId = "system-settings",
                    poolVersion = 1,
                    entryId = "android.role.home",
                    title = "title.role.home",
                    description = "desc.role.home",
                    check = CheckSpec.AndroidRole(role = "android.app.role.HOME"),
                    apply = ApplySpec.AndroidRoleRequest(role = "android.app.role.HOME"),
                    criticality = Criticality.Required,
                ),
                Config(
                    id = "ui.font.large",
                    poolId = "ui-customization",
                    poolVersion = 1,
                    entryId = "ui.font.large",
                    title = "title.font.large",
                    description = "desc.font.large",
                    check = CheckSpec.UIFont(minScale = 1.3f),
                    apply = ApplySpec.InAppOnly,
                    criticality = Criticality.Optional,
                ),
            ),
            abstractProfile = AbstractProfile(
                layout = Layout(
                    screens = listOf(
                        Screen(
                            id = "home",
                            grid = Grid(
                                rows = 2,
                                cols = 2,
                                slots = listOf(
                                    Slot(row = 0, col = 0),
                                    Slot(row = 1, col = 1, kind = "future-tile-hook"),
                                ),
                            ),
                        ),
                    ),
                ),
                bindings = listOf(
                    Binding(
                        slotPosition = SlotPosition("home", 0, 0),
                        targetPackage = "com.example.app",
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(Preset.serializer(), original)
        val decoded = json.decodeFromString(Preset.serializer(), encoded)

        assertEquals(original, decoded)
        assertEquals(PresetRef(uid = "com.launcher.preset.test", version = 2), decoded.ref)
    }
}
