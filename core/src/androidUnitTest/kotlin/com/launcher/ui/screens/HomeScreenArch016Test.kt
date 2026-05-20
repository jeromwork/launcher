package com.launcher.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.launcher.adapters.config.ConfigBackedFlowRepository
import com.launcher.api.action.DispatchResult
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.Contact
import com.launcher.api.config.ElementId
import com.launcher.api.config.Flow as ConfigFlow
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.config.Slot
import com.launcher.api.config.SlotKind
import com.launcher.api.identity.AdminIdentity
import com.launcher.api.link.Link
import com.launcher.fake.config.FakeConfigEditor
import com.launcher.fake.config.FakeLocalConfigStore
import com.launcher.fake.link.FakeLinkRegistry
import com.launcher.fake.sync.FakeRemoteSyncBackend
import com.launcher.ui.navigation.HomeComponent
import com.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spec 010 T039 — ARCH-016 closure verification: HomeScreen renders from
 * `/config/current` (via [ConfigBackedFlowRepository]) — not the deleted
 * MockFlowRepository / flows_mock_*.json assets.
 *
 * Three scenarios:
 *  1. seeded config → flow + slot label render;
 *  2. cold start с already-applied config (US-1 #2: offline cold-start renders
 *     last-applied without network) — simulated by writing applied config
 *     into FakeLocalConfigStore before constructing the component;
 *  3. no paired link → empty state («Загрузка…») per FR-005 fallback semantics
 *     (preset bundling moves to spec 010 Phase 3 wizard — current behaviour
 *     is empty until /config seeded).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class HomeScreenArch016Test {

    @get:Rule
    val rule = createComposeRule()

    private val maria = Contact(
        id = ElementId("11111111-1111-4111-8111-111111111111"),
        displayName = "Маша",
        phoneNumber = "+79161234567",
    )

    private val sampleLinkId = "test-link-0001"

    private fun seededConfig(): ConfigDocument = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 1000L, nanoseconds = 0),
        lastWriterDeviceId = "admin-uid",
        presetId = "simple-launcher",
        flows = listOf(
            ConfigFlow(
                id = ElementId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                title = "Контакты",
                slots = listOf(
                    Slot(
                        id = ElementId("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                        kind = SlotKind.Call,
                        args = buildJsonObject {
                            put("contactId", JsonPrimitive(maria.id.value))
                        },
                    ),
                ),
            ),
        ),
        contacts = listOf(maria),
    )

    private fun newContext(): DefaultComponentContext {
        val lr = LifecycleRegistry()
        lr.resume()
        return DefaultComponentContext(lifecycle = lr)
    }

    private fun pairedLink(): Link = Link(
        linkId = sampleLinkId,
        adminId = AdminIdentity("admin-uid"),
        managedDeviceId = "managed-device-uid",
        managedDeviceFirebaseUid = "managed-fb-uid",
        createdAt = 1000L,
    )

    @Test
    fun seeded_applied_config_renders_flow_label(): Unit = runBlocking {
        val store = FakeLocalConfigStore()
        store.writeAppliedConfig(sampleLinkId, seededConfig())
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "managed-fb-uid")
        val registry = FakeLinkRegistry(
            backend = FakeRemoteSyncBackend(),
            initial = pairedLink(),
        )
        val repo = ConfigBackedFlowRepository(editor, registry)
        val component = HomeComponent(
            componentContext = newContext(),
            flowRepository = repo,
            dispatchAction = { DispatchResult.Ok },
            onSettingsClick = {},
            onAddFlowClick = {},
            onAdminDevicesClick = {},
            onAddSlotClick = {},
        )
        rule.setContent {
            LauncherTheme(preset = "simple-launcher") { HomeScreen(component = component) }
        }
        rule.waitForIdle()
        // The flow's «Контакты» tab from the seeded /config/current must render
        // in the bottom flow bar.
        rule.onNodeWithText("Контакты").assertIsDisplayed()
    }

    @Test
    fun offline_cold_start_renders_last_applied(): Unit = runBlocking {
        // US-1 #2: simulate the device starting up offline. We pre-populate
        // the local applied store (как if a previous online apply succeeded)
        // and verify the home screen renders from it without further network.
        val store = FakeLocalConfigStore()
        store.writeAppliedConfig(sampleLinkId, seededConfig())
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "managed-fb-uid")
        // Empty remote backend — simulates no network connection.
        val registry = FakeLinkRegistry(
            backend = FakeRemoteSyncBackend(),
            initial = pairedLink(),
        )
        val component = HomeComponent(
            componentContext = newContext(),
            flowRepository = ConfigBackedFlowRepository(editor, registry),
            dispatchAction = { DispatchResult.Ok },
            onSettingsClick = {},
            onAddFlowClick = {},
            onAdminDevicesClick = {},
            onAddSlotClick = {},
        )
        rule.setContent {
            LauncherTheme(preset = "simple-launcher") { HomeScreen(component = component) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Контакты").assertIsDisplayed()
    }

    @Test
    fun no_paired_link_shows_loading_state(): Unit = runBlocking {
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "managed-fb-uid")
        val registry = FakeLinkRegistry(
            backend = FakeRemoteSyncBackend(),
            initial = null, // never paired
        )
        val component = HomeComponent(
            componentContext = newContext(),
            flowRepository = ConfigBackedFlowRepository(editor, registry),
            dispatchAction = { DispatchResult.Ok },
            onSettingsClick = {},
            onAddFlowClick = {},
            onAdminDevicesClick = {},
            onAddSlotClick = {},
        )
        rule.setContent {
            LauncherTheme(preset = "simple-launcher") { HomeScreen(component = component) }
        }
        rule.waitForIdle()
        // Without a link, the flow list is empty → the placeholder loading
        // text from HomeScreen is shown. FR-005 preset-fallback content is
        // populated by the spec 010 Phase 3 wizard — TODO[preset-fallback]
        // covers the senior-safe path once the wizard seeds /config/current.
        rule.onNodeWithText("Загрузка…").assertIsDisplayed()
    }
}
