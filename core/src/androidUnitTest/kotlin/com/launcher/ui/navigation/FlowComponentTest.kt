package com.launcher.ui.navigation

import family.wire.WireVersion

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests US-508 dispatch-result plumbing on [FlowComponent]:
 *  - Tap → dispatchAction is called once with the slot's Action.
 *  - retryLastAction re-issues the same Action.
 *  - acknowledgeDispatchResult clears `lastDispatchResult` so a second
 *    snackbar is not shown for the same outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setMainDispatcher() = Dispatchers.setMain(testDispatcher)

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val phoneAction = Action(
        providerId = ProviderId.PHONE,
        payload = ActionPayload.Phone("+1"),
    )
    private val sampleFlow = FlowDescriptor(
        schemaVersion = WireVersion(1, 0),
        id = "f",
        name = "Family",
        templateId = "contacts",
        slots = listOf(
            SlotDescriptor(id = "s1", label = "Anna", iconRef = "", action = phoneAction),
            SlotDescriptor(id = "s2", label = "Empty", iconRef = "", action = null),
        ),
    )

    private fun startedContext(): DefaultComponentContext {
        val lr = LifecycleRegistry()
        lr.resume()
        return DefaultComponentContext(lifecycle = lr)
    }

    private class FakeRepo(private val flows: List<FlowDescriptor>) : FlowRepository {
        override suspend fun loadFlows() = flows
        override fun availableTemplates(presetId: String) = emptyList<FlowTemplate>()
        // Spec 010 T029 — observeFlows emits the seeded list once.
        override fun observeFlows(): kotlinx.coroutines.flow.Flow<List<FlowDescriptor>> =
            kotlinx.coroutines.flow.flowOf(flows)
        override suspend fun addFlow(templateId: String) = error("not used in this test")
    }

    @Test
    fun tap_callsDispatchOnce_withSlotAction() = runTest(testDispatcher) {
        val calls = mutableListOf<Action>()
        val component = FlowComponent(
            componentContext = startedContext(),
            flowId = sampleFlow.id,
            flowRepository = FakeRepo(listOf(sampleFlow)),
            dispatchAction = { action ->
                calls += action
                DispatchResult.Ok
            },
        )
        advanceUntilIdle()
        component.onSlotTap(sampleFlow.slots[0])
        advanceUntilIdle()
        assertEquals(listOf(phoneAction), calls)
        assertTrue(component.state.value.lastDispatchResult is DispatchResult.Ok)
    }

    @Test
    fun tap_onPlaceholder_isNoOp() = runTest(testDispatcher) {
        val calls = mutableListOf<Action>()
        val component = FlowComponent(
            componentContext = startedContext(),
            flowId = sampleFlow.id,
            flowRepository = FakeRepo(listOf(sampleFlow)),
            dispatchAction = { action ->
                calls += action
                DispatchResult.Ok
            },
        )
        advanceUntilIdle()
        component.onSlotTap(sampleFlow.slots[1]) // placeholder
        advanceUntilIdle()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun retry_redispatches_lastAction() = runTest(testDispatcher) {
        val calls = mutableListOf<Action>()
        val component = FlowComponent(
            componentContext = startedContext(),
            flowId = sampleFlow.id,
            flowRepository = FakeRepo(listOf(sampleFlow)),
            dispatchAction = { action ->
                calls += action
                DispatchResult.Failure("boom")
            },
        )
        advanceUntilIdle()
        component.onSlotTap(sampleFlow.slots[0])
        advanceUntilIdle()
        component.retryLastAction()
        advanceUntilIdle()
        assertEquals(2, calls.size)
        assertEquals(phoneAction, calls[0])
        assertEquals(phoneAction, calls[1])
    }

    @Test
    fun acknowledge_clearsLastDispatchResult() = runTest(testDispatcher) {
        val component = FlowComponent(
            componentContext = startedContext(),
            flowId = sampleFlow.id,
            flowRepository = FakeRepo(listOf(sampleFlow)),
            dispatchAction = { DispatchResult.Failure("x") },
        )
        advanceUntilIdle()
        component.onSlotTap(sampleFlow.slots[0])
        advanceUntilIdle()
        assertTrue(component.state.value.lastDispatchResult is DispatchResult.Failure)
        component.acknowledgeDispatchResult()
        assertNull(component.state.value.lastDispatchResult)
    }
}
