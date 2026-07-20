package com.launcher.ui.navigation

import com.launcher.wire.WireVersion

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowRepository
import com.launcher.api.FlowTemplate
import com.launcher.api.action.DispatchResult
import com.launcher.preset.query.entity
import com.launcher.preset.query.profileOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeComponentLoadingStateTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun createSampleFlow(id: String) = FlowDescriptor(
        schemaVersion = WireVersion(1, 0),
        id = id,
        name = "Flow $id",
        templateId = "template-$id",
        slots = emptyList(),
    )

    private fun startedContext(): DefaultComponentContext {
        val lr = LifecycleRegistry()
        lr.resume()
        return DefaultComponentContext(lifecycle = lr)
    }

    private class FakeFlowRepository(
        var flowsToReturn: List<FlowDescriptor> = emptyList(),
        var delayMs: Long = 0,
        var exceptionToThrow: Throwable? = null,
    ) : FlowRepository {
        var loadAttempts = 0
        var cancelledCount = 0

        override suspend fun loadFlows(): List<FlowDescriptor> {
            loadAttempts++
            try {
                if (delayMs > 0) {
                    delay(delayMs)
                }
            } catch (e: CancellationException) {
                cancelledCount++
                throw e
            }
            exceptionToThrow?.let { throw it }
            return flowsToReturn
        }

        override fun availableTemplates(presetId: String): List<FlowTemplate> = emptyList()

        override fun observeFlows(): Flow<List<FlowDescriptor>> = flowOf(flowsToReturn)

        override suspend fun addFlow(templateId: String): FlowDescriptor = error("not used")
    }

    private fun createComponent(
        repository: FlowRepository,
        onResetData: () -> Unit = {},
    ) = HomeComponent(
        componentContext = startedContext(),
        flowRepository = repository,
        dispatchAction = { DispatchResult.Ok },
        onSettingsClick = {},
        onAddFlowClick = {},
        onAdminDevicesClick = {},
        onAddSlotClick = {},
        onResetData = onResetData,
    )

    // ---- TASK-127 T127-027: the regression path, end-to-end ----
    //
    // These use the REAL ProfileBackedFlowRepository over a FakeProfileStore, so
    // they exercise what actually broke: the wizard saved a Profile, the home
    // screen read a ConfigDocument nobody filled, and HomeComponent rendered
    // Error. A fake FlowRepository would prove nothing about that wiring.

    // Entities/profile built via the shared QueryFixtures helpers, which stamp the
    // default semantic tags the ProfileBackedFlowRepository queries on (tiles get
    // {Presentation,Tile}, flows get {Presentation,Flow}, ...).

    @Test
    fun postManifestWizardReconcile_profileSeeded_homeReady() = runTest(testDispatcher) {
        // Exactly the fresh-install shape: the wizard has just saved a Profile.
        val store = com.launcher.preset.fakes.FakeProfileStore(
            profileOf(
                entity(
                    "tile-settings",
                    com.launcher.preset.model.Component.AppTile(
                        packageName = "com.android.settings",
                        labelKey = "tile_settings",
                    ),
                ),
            ),
        )
        val component = createComponent(
            com.launcher.adapters.flow.ProfileBackedFlowRepository(store),
        )

        assertEquals(HomeLoadingState.Loading, component.loadingState.value)
        advanceUntilIdle()

        // Before TASK-127 this was Error("flows empty").
        assertEquals(HomeLoadingState.Ready("default"), component.loadingState.value)
        assertEquals(1, component.state.value.flows.single().slots.size)
    }

    @Test
    fun hierarchicalProfile_rendersFlowsAndSwitches() = runTest(testDispatcher) {
        val store = com.launcher.preset.fakes.FakeProfileStore(
            profileOf(
                entity("ws", com.launcher.preset.model.Component.Workspace()),
                entity(
                    "flow-calls",
                    com.launcher.preset.model.Component.Flow(titleKey = "flow.calls", order = 0),
                    parentId = "ws",
                ),
                entity(
                    "flow-apps",
                    com.launcher.preset.model.Component.Flow(titleKey = "flow.apps", order = 1),
                    parentId = "ws",
                ),
                entity(
                    "tile-wa",
                    com.launcher.preset.model.Component.AppTile("com.whatsapp", "l"),
                    parentId = "flow-calls",
                ),
                entity(
                    "tile-settings",
                    com.launcher.preset.model.Component.AppTile("com.android.settings", "l"),
                    parentId = "flow-apps",
                ),
            ),
        )
        val component = createComponent(
            com.launcher.adapters.flow.ProfileBackedFlowRepository(store),
        )
        advanceUntilIdle()

        // Two tabs, ordered by Flow.order, each carrying only its own tile.
        assertEquals(listOf("flow-calls", "flow-apps"), component.state.value.flows.map { it.id })
        assertEquals(HomeLoadingState.Ready("flow-calls"), component.loadingState.value)

        // Switching tabs is the existing TASK-52 mechanism — no Activity restart,
        // no repository round-trip.
        component.selectFlow("flow-apps")
        advanceUntilIdle()

        assertEquals("flow-apps", component.state.value.activeFlowId)
        assertEquals(HomeLoadingState.Ready("flow-apps"), component.loadingState.value)
    }

    @Test
    fun emptyProfileStore_staysLoading_thenTimesOut_ratherThanShowingEmptyScreen() = runTest(testDispatcher) {
        val store = com.launcher.preset.fakes.FakeProfileStore(initial = null)
        val component = createComponent(
            com.launcher.adapters.flow.ProfileBackedFlowRepository(store),
        )

        // A transient null must not flash Error (SEQ-4)...
        advanceTimeBy(1_000)
        assertEquals(HomeLoadingState.Loading, component.loadingState.value)

        // ...but a Profile that never arrives is a real failure, surfaced by the
        // existing 3s timeout with Retry — not an infinite spinner.
        advanceTimeBy(3_000)
        assertEquals(HomeLoadingState.Error("timeout 3s"), component.loadingState.value)
    }

    @Test
    fun profileArrivingLate_movesLoadingToReady() = runTest(testDispatcher) {
        val store = com.launcher.preset.fakes.FakeProfileStore(initial = null)
        val component = createComponent(
            com.launcher.adapters.flow.ProfileBackedFlowRepository(store),
        )
        advanceTimeBy(500)
        assertEquals(HomeLoadingState.Loading, component.loadingState.value)

        store.save(
            profileOf(
                entity("tile", com.launcher.preset.model.Component.AppTile("com.a", "l")),
            ),
        )
        advanceUntilIdle()

        assertEquals(HomeLoadingState.Ready("default"), component.loadingState.value)
    }

    @Test
    fun loading_to_ready_on_non_empty_flows() = runTest(testDispatcher) {
        val flows = List(6) { index -> createSampleFlow("flow-$index") }
        val repo = FakeFlowRepository(flowsToReturn = flows)
        val component = createComponent(repo)

        assertEquals(HomeLoadingState.Loading, component.loadingState.value)
        advanceUntilIdle()
        assertEquals(HomeLoadingState.Ready("flow-0"), component.loadingState.value)
    }

    @Test
    fun loading_to_error_on_empty_flows() = runTest(testDispatcher) {
        val repo = FakeFlowRepository(flowsToReturn = emptyList())
        val component = createComponent(repo)

        advanceUntilIdle()
        assertEquals(HomeLoadingState.Error("flows empty"), component.loadingState.value)
    }

    @Test
    fun loading_to_error_on_timeout() = runTest(testDispatcher) {
        val repo = FakeFlowRepository(delayMs = 10_000)
        val component = createComponent(repo)

        assertEquals(HomeLoadingState.Loading, component.loadingState.value)
        advanceTimeBy(3500)
        assertEquals(HomeLoadingState.Error("timeout 3s"), component.loadingState.value)
        assertTrue(component.loadingState.value !is HomeLoadingState.Loading)
    }

    @Test
    fun loading_to_error_on_exception() = runTest(testDispatcher) {
        val repo = FakeFlowRepository(exceptionToThrow = IllegalStateException("boom"))
        val component = createComponent(repo)

        advanceUntilIdle()
        assertEquals(HomeLoadingState.Error("exception: boom"), component.loadingState.value)
    }

    @Test
    fun retry_after_error_relaunches() = runTest(testDispatcher) {
        val repo = FakeFlowRepository(flowsToReturn = emptyList())
        val component = createComponent(repo)

        advanceUntilIdle()
        assertEquals(HomeLoadingState.Error("flows empty"), component.loadingState.value)

        repo.flowsToReturn = listOf(createSampleFlow("flow-1"))
        component.retry()

        assertEquals(HomeLoadingState.Loading, component.loadingState.value)
        advanceUntilIdle()
        assertEquals(HomeLoadingState.Ready("flow-1"), component.loadingState.value)
    }

    @Test
    fun retry_cancels_previous_pending_job() = runTest(testDispatcher) {
        val repo = FakeFlowRepository(delayMs = 10_000)
        val component = createComponent(repo)

        advanceTimeBy(1000)
        assertEquals(1, repo.loadAttempts)
        assertEquals(0, repo.cancelledCount)

        component.retry()
        advanceTimeBy(100)

        assertEquals(1, repo.cancelledCount)
        assertEquals(2, repo.loadAttempts)
    }

    @Test
    fun reset_confirmation_state_transitions() = runTest(testDispatcher) {
        var resetCalled = false
        val component = createComponent(
            repository = FakeFlowRepository(),
            onResetData = { resetCalled = true }
        )

        assertFalse(component.resetDialogVisible.value)

        component.showResetConfirmation()
        assertTrue(component.resetDialogVisible.value)

        component.hideResetConfirmation()
        assertFalse(component.resetDialogVisible.value)

        component.showResetConfirmation()
        assertTrue(component.resetDialogVisible.value)
        component.confirmReset()
        assertFalse(component.resetDialogVisible.value)
        assertTrue(resetCalled)
    }

    @Test
    fun lifecycle_cancel_during_launchLoadFlows_rethrows_cancellation() = runTest(testDispatcher) {
        val repo = FakeFlowRepository(delayMs = 5000)
        val lr = LifecycleRegistry()
        lr.resume()
        val context = DefaultComponentContext(lifecycle = lr)
        val component = HomeComponent(
            componentContext = context,
            flowRepository = repo,
            dispatchAction = { DispatchResult.Ok },
            onSettingsClick = {},
            onAddFlowClick = {},
            onAdminDevicesClick = {},
            onAddSlotClick = {},
            onResetData = {},
        )

        advanceTimeBy(1000)
        assertEquals(HomeLoadingState.Loading, component.loadingState.value)

        lr.destroy()
        advanceUntilIdle()
        assertEquals(HomeLoadingState.Loading, component.loadingState.value)
        assertEquals(1, repo.cancelledCount)
    }
}
