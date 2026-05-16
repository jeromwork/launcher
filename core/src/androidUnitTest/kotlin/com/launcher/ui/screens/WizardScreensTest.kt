package com.launcher.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.launcher.api.action.NotApplicableReason
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderState
import com.launcher.test.FakeProviderRegistry
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

    private fun newContext(): DefaultComponentContext {
        val lr = LifecycleRegistry()
        lr.resume()
        return DefaultComponentContext(lifecycle = lr)
    }

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

    private fun wizardWith(registry: FakeProviderRegistry): AddSlotWizardComponent =
        AddSlotWizardComponent(
            componentContext = newContext(),
            flowId = "flow_main",
            onBack = {},
            onDone = {},
            providerRegistry = registry,
        )

    @Test
    fun addSlotWizard_emptyRegistry_showsEmptyState() {
        val component = wizardWith(FakeProviderRegistry(initial = emptyList()))
        rule.setContent {
            LauncherTheme(preset = "workspace") { AddSlotWizardScreen(component = component) }
        }
        rule.onNodeWithText("Новый слот").assertIsDisplayed()
        rule.onNodeWithTag("add_slot_empty").assertIsDisplayed()
    }

    @Test
    fun addSlotWizard_availableProvider_isShownPlain() {
        val registry = FakeProviderRegistry(
            initial = listOf(
                ProviderState(ProviderId.PHONE, ProviderAvailability.Available),
            ),
        )
        rule.setContent {
            LauncherTheme(preset = "workspace") { AddSlotWizardScreen(component = wizardWith(registry)) }
        }
        rule.onNodeWithTag("add_slot_provider_phone").assertIsDisplayed()
        rule.onNodeWithText("Позвонить").assertIsDisplayed()
    }

    @Test
    fun addSlotWizard_missingProvider_isShownAsInstall() {
        val registry = FakeProviderRegistry(
            initial = listOf(
                ProviderState(ProviderId.WHATSAPP, ProviderAvailability.Missing(installHint = null)),
            ),
        )
        rule.setContent {
            LauncherTheme(preset = "workspace") { AddSlotWizardScreen(component = wizardWith(registry)) }
        }
        rule.onNodeWithTag("add_slot_provider_whatsapp_missing").assertIsDisplayed()
    }

    @Test
    fun addSlotWizard_notApplicable_isShownGreyed() {
        val registry = FakeProviderRegistry(
            initial = listOf(
                ProviderState(
                    ProviderId.SMS,
                    ProviderAvailability.NotApplicable(NotApplicableReason.NoDefaultSmsApp),
                ),
            ),
        )
        rule.setContent {
            LauncherTheme(preset = "workspace") { AddSlotWizardScreen(component = wizardWith(registry)) }
        }
        rule.onNodeWithTag("add_slot_provider_sms_na").assertIsDisplayed()
    }

    @Test
    fun addSlotWizard_clickProvider_enablesDone() {
        val registry = FakeProviderRegistry(
            initial = listOf(ProviderState(ProviderId.PHONE, ProviderAvailability.Available)),
        )
        rule.setContent {
            LauncherTheme(preset = "workspace") { AddSlotWizardScreen(component = wizardWith(registry)) }
        }
        rule.onNodeWithTag("add_slot_done").assertIsDisplayed()
        rule.onNodeWithTag("add_slot_provider_phone").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("add_slot_done").assertIsEnabled()
    }

    @Test
    fun adminDevicesRendersEmptyState() {
        rule.setContent {
            LauncherTheme(preset = "workspace") {
                AdminDevicesScreen(
                    component = AdminDevicesComponent(
                        componentContext = newContext(),
                        linkRegistry = com.launcher.fake.link.FakeLinkRegistry(
                            backend = com.launcher.fake.sync.FakeRemoteSyncBackend(),
                        ),
                        onBack = {},
                        onEditLink = {},
                        onHistoryLink = {},
                        onContactsLink = {},
                        onHealthLink = {},
                    ),
                )
            }
        }
        rule.onNodeWithText("Устройства").assertIsDisplayed()
        rule.onNodeWithText("Нет сопряжённых устройств").assertIsDisplayed()
    }
}
