package com.launcher.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.launcher.ui.navigation.AddFlowWizardComponent
import com.launcher.ui.navigation.AddSlotWizardComponent
import com.launcher.ui.navigation.AdminDevicesComponent
import com.launcher.ui.theme.LauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class WizardScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private fun newContext(): DefaultComponentContext =
        DefaultComponentContext(lifecycle = LifecycleRegistry())

    @Test
    fun addFlowWizardRendersTitle() {
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                AddFlowWizardScreen(
                    component = AddFlowWizardComponent(
                        componentContext = newContext(),
                        onBack = {},
                        onDone = {},
                    ),
                )
            }
        }
        rule.onNodeWithText("Новый flow").assertIsDisplayed()
        rule.onNodeWithText("Контакты").assertIsDisplayed()
    }

    @Test
    fun addSlotWizardRendersForFlowId() {
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                AddSlotWizardScreen(
                    component = AddSlotWizardComponent(
                        componentContext = newContext(),
                        flowId = "flow_main",
                        onBack = {},
                        onDone = {},
                    ),
                )
            }
        }
        rule.onNodeWithText("Новый слот").assertIsDisplayed()
        rule.onNodeWithText("Позвонить").assertIsDisplayed()
    }

    @Test
    fun adminDevicesRendersEmptyState() {
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                AdminDevicesScreen(
                    component = AdminDevicesComponent(
                        componentContext = newContext(),
                        onBack = {},
                    ),
                )
            }
        }
        rule.onNodeWithText("Устройства").assertIsDisplayed()
        rule.onNodeWithText("Нет сопряжённых устройств").assertIsDisplayed()
    }
}
