package com.launcher.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowRepository
import com.launcher.api.FlowTemplate
import com.launcher.api.SlotDescriptor
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderId
import com.launcher.api.action.WhatsAppCallKind
import com.launcher.ui.navigation.FlowComponent
import com.launcher.ui.navigation.HomeComponent
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
class HomeAndFlowScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private class FakeFlowRepository(private val flows: List<FlowDescriptor>) : FlowRepository {
        override suspend fun loadFlows(): List<FlowDescriptor> = flows
        override fun availableTemplates(presetId: String): List<FlowTemplate> = emptyList()
    }

    private val sampleFlow = FlowDescriptor(
        schemaVersion = 1,
        id = "flow_main",
        name = "Главная",
        templateId = "contacts",
        slots = listOf(
            SlotDescriptor(
                id = "slot_anna",
                label = "Анна",
                iconRef = "ic_anna",
                action = Action(
                    providerId = ProviderId.WHATSAPP,
                    payload = ActionPayload.WhatsAppCall("contact_anna", WhatsAppCallKind.VOICE),
                ),
            ),
            SlotDescriptor(
                id = "slot_oleg",
                label = "Олег",
                iconRef = "ic_oleg",
                action = Action(
                    providerId = ProviderId.WHATSAPP,
                    payload = ActionPayload.WhatsAppCall("contact_oleg", WhatsAppCallKind.VOICE),
                ),
            ),
        ),
    )

    private fun startedContext(): DefaultComponentContext {
        val lr = LifecycleRegistry()
        lr.resume()
        return DefaultComponentContext(lifecycle = lr)
    }

    @Test
    fun homeRendersTilesFromFakeRepository() {
        val home = HomeComponent(
            componentContext = startedContext(),
            flowRepository = FakeFlowRepository(listOf(sampleFlow)),
            dispatchAction = { DispatchResult.Ok },
            onSettingsClick = {},
            onAddFlowClick = {},
            onAdminDevicesClick = {},
            onAddSlotClick = {},
        )

        rule.setContent {
            LauncherTheme(preset = "workspace") {
                HomeScreen(component = home)
            }
        }

        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("Анна").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Анна").assertIsDisplayed()
        rule.onNodeWithText("Олег").assertIsDisplayed()
    }

    @Test
    fun flowScreenRendersSlotsForGivenFlowId() {
        val flow = FlowComponent(
            componentContext = startedContext(),
            flowId = sampleFlow.id,
            flowRepository = FakeFlowRepository(listOf(sampleFlow)),
            dispatchAction = { DispatchResult.Ok },
        )

        rule.setContent {
            LauncherTheme(preset = "workspace") {
                FlowScreen(component = flow)
            }
        }

        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("Анна").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Анна").assertIsDisplayed()
        rule.onNodeWithText("Олег").assertIsDisplayed()
    }
}
