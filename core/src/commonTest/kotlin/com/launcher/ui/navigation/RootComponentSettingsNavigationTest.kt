package com.launcher.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowPreset
import com.launcher.api.FlowRepository
import com.launcher.api.FlowTemplate
import com.launcher.api.PresetRepository
import com.launcher.api.action.DispatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TASK-69 T069-025 (SC-009) — after absorbing the legacy Settings screen there
 * is exactly one settings entry point: `RootComponent` never produces a
 * Settings child (compile-time — `RootConfig`/`RootChild` no longer declare
 * one), and `HomeComponent`'s "Settings" tap invokes the host's
 * `onOpenSettings` callback (which the real app wires to launch
 * `SettingsActivity`) instead of pushing a Decompose child.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentSettingsNavigationTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private class FakePresetRepository(initial: FlowPreset = FlowPreset.WORKSPACE) : PresetRepository {
        private val active = MutableStateFlow<FlowPreset?>(initial)
        override suspend fun getActivePreset(): FlowPreset? = active.value
        override suspend fun setActivePreset(preset: FlowPreset) { active.value = preset }
        override suspend fun clear() { active.value = null }
        override fun observeActivePreset(): Flow<FlowPreset?> = active
    }

    private class FakeFlowRepository : FlowRepository {
        override suspend fun loadFlows(): List<FlowDescriptor> = emptyList()
        override fun availableTemplates(presetId: String): List<FlowTemplate> = emptyList()
        override fun observeFlows(): Flow<List<FlowDescriptor>> = flowOf(emptyList())
        override suspend fun addFlow(templateId: String): FlowDescriptor = error("not used")
    }

    private fun startedContext(): DefaultComponentContext {
        val lr = LifecycleRegistry()
        lr.resume()
        return DefaultComponentContext(lifecycle = lr)
    }

    @Test
    fun homeSettingsTap_invokesOnOpenSettings_notADecomposePush() = runTest(testDispatcher) {
        var settingsOpened = false
        val root = RootComponent(
            componentContext = startedContext(),
            presetRepository = FakePresetRepository(),
            flowRepository = FakeFlowRepository(),
            dispatchAction = { DispatchResult.Ok },
            onResetData = {},
            onOpenPairing = {},
            onOpenScanner = {},
            onOpenSettings = { settingsOpened = true },
            initialPresetSlug = "simple-launcher",
        )

        val homeChild = root.stack.value.items
            .map { it.instance }
            .filterIsInstance<RootChild.Home>()
            .single()

        assertFalse(settingsOpened, "must not fire before the tap")
        homeChild.component.onSettingsClick()
        assertTrue(settingsOpened, "Settings tap must invoke the host callback (re-hosted as an Activity, FR-017)")

        // SC-009: no RootChild in the stack is ever a Settings screen — the
        // legacy entry point no longer exists (compile-time guarantee: RootChild
        // has no Settings variant, so this filter is vacuously true and kept as
        // a readable regression pin rather than a real runtime risk).
        assertTrue(root.stack.value.items.none { it.instance::class.simpleName == "Settings" })
    }
}
