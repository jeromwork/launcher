package com.launcher.api.switchstrategy

import com.launcher.api.preset.AbstractProfile
import com.launcher.api.preset.Config
import com.launcher.api.preset.Criticality
import com.launcher.api.preset.PRESET_SCHEMA_VERSION
import com.launcher.api.preset.Preset
import com.launcher.api.profile.AppliedState
import com.launcher.api.profile.Grid
import com.launcher.api.profile.Layout
import com.launcher.api.profile.Screen
import com.launcher.api.profile.Slot
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CopyOnActivateStrategyTest {

    private val strategy = CopyOnActivateStrategy()

    private fun preset(abstract: AbstractProfile?): Preset = Preset(
        schemaVersion = PRESET_SCHEMA_VERSION,
        uid = "com.launcher.preset.test",
        version = 1,
        slug = "test",
        label = "l",
        description = "d",
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
                criticality = Criticality.Optional,
            ),
        ),
        abstractProfile = abstract,
    )

    @Test
    fun copiesAbstractProfileLayoutAndBindings() {
        val layout = Layout(
            screens = listOf(Screen("h", Grid(1, 1, listOf(Slot(0, 0))))),
        )
        val abstract = AbstractProfile(layout = layout, bindings = emptyList())
        val result = strategy.migrate(from = null, toPreset = preset(abstract))

        assertEquals(layout, result.layout)
        assertEquals(1, result.settings.size)
        assertTrue(result.settings.all { it.state == AppliedState.NotApplied })
    }

    @Test
    fun emptyLayoutWhenAbstractProfileIsNull() {
        val result = strategy.migrate(from = null, toPreset = preset(abstract = null))
        assertEquals(Layout.empty(), result.layout)
        assertEquals(1, result.settings.size)
        assertEquals(AppliedState.NotApplied, result.settings.first().state)
    }

    @Test
    fun ignoresFromArgumentPerContract() {
        val abstract = AbstractProfile(layout = Layout.empty(), bindings = emptyList())
        val ignored = strategy.migrate(from = null, toPreset = preset(abstract))
        val also = strategy.migrate(
            from = com.launcher.api.profile.ProfileData(layout = Layout(listOf(Screen("x", Grid(9, 9))))),
            toPreset = preset(abstract),
        )
        assertEquals(ignored, also)
    }
}
