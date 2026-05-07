package com.launcher.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.launcher.api.FlowPreset
import com.launcher.ui.theme.LauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class FirstLaunchScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val presets = listOf(
        PresetUiModel(FlowPreset.WORKSPACE, "Workspace", "Contacts at hand"),
        PresetUiModel(FlowPreset.LAUNCHER, "Launcher", "Replaces home"),
        PresetUiModel(FlowPreset.SIMPLE_LAUNCHER, "Simple", "Large buttons"),
    )

    @Test
    fun rendersAllThreePresetCards() {
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                FirstLaunchScreen(presets = presets, onPresetSelected = {})
            }
        }

        rule.onNodeWithTag("preset_card_workspace").assertIsDisplayed()
        rule.onNodeWithTag("preset_card_launcher").assertIsDisplayed()
        rule.onNodeWithTag("preset_card_simple-launcher").assertIsDisplayed()
        rule.onNodeWithText("Workspace").assertIsDisplayed()
    }

    @Test
    fun tappingPresetInvokesCallbackWithExpectedValue() {
        val selected = mutableListOf<FlowPreset>()
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                FirstLaunchScreen(presets = presets, onPresetSelected = { selected.add(it) })
            }
        }

        rule.onNodeWithTag("preset_card_simple-launcher").performClick()

        assertEquals(listOf(FlowPreset.SIMPLE_LAUNCHER), selected)
    }
}
