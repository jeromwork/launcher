package com.launcher.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.ui.navigation.SettingsComponent
import com.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private class FakePresetRepository(initial: FlowPreset = FlowPreset.WORKSPACE) : PresetRepository {
        private val state = MutableStateFlow<FlowPreset?>(initial)
        override suspend fun getActivePreset(): FlowPreset? = state.value
        override suspend fun setActivePreset(preset: FlowPreset) { state.value = preset }
        override suspend fun clear() { state.value = null }
        override fun observeActivePreset(): Flow<FlowPreset?> = state
    }

    private fun newComponent(
        repo: PresetRepository = FakePresetRepository(),
        onBack: () -> Unit = {},
        onPresetChanged: () -> Unit = {},
        onResetData: () -> Unit = {},
    ) = SettingsComponent(
        componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
        presetRepository = repo,
        onBack = onBack,
        onPresetChanged = onPresetChanged,
        onResetData = onResetData,
    )

    @Test
    fun rendersTitleAndDefaultPreset() {
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                SettingsScreen(component = newComponent())
            }
        }
        rule.onNodeWithText("Настройки").assertIsDisplayed()
        rule.onNodeWithTag("settings_change_preset").assertIsDisplayed()
    }

    @Test
    fun toggleRemoteControlRevealsQrPlaceholderRow() {
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                SettingsScreen(component = newComponent())
            }
        }
        rule.onNodeWithTag("settings_remote_toggle").performClick()
        rule.onNodeWithText("Показать QR-код").assertIsDisplayed()
    }

    @Test
    fun backButtonInvokesCallback() {
        var backCount = 0
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                SettingsScreen(component = newComponent(onBack = { backCount++ }))
            }
        }
        rule.onNodeWithTag("settings_back").performClick()
        assertTrue(backCount >= 1)
    }
}
