package com.launcher.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowRepository
import com.launcher.api.FlowTemplate
import com.launcher.api.action.DispatchResult
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
        schemaVersion = 1,
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
}
