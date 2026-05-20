package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.FlowRepository
import com.launcher.api.SlotDescriptor
import com.launcher.api.action.Action
import com.launcher.api.action.DispatchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Component for one flow's slot grid.
 *
 * Spec 005 migration: this component now hands off [Action] objects directly
 * to the new dispatcher pipeline. The confirmation/warning overlay flow used
 * for spec 002 WhatsApp tiles is **temporarily removed** — Phase 5 (US-508)
 * brings it back as a snackbar-based universal mechanism (any provider).
 *
 * The dispatch callback type changed from
 * `(ActionRequest) -> DispatchResult` (spec 002 path) to
 * `suspend (Action) -> DispatchResult` (spec 005 path). The Activity wires
 * the lambda; component stays platform-agnostic.
 */
class FlowComponent(
    componentContext: ComponentContext,
    val flowId: String,
    private val flowRepository: FlowRepository,
    private val dispatchAction: suspend (Action) -> DispatchResult,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(FlowUiState())
    val state: StateFlow<FlowUiState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        // Spec 010 T030 — observe Flow, не одноразовый loadFlows().
        // When admin pushes new config (ARCH-016 closure), the matching flow's
        // slots arrive without explicit refresh.
        flowRepository.observeFlows()
            .onEach { flows ->
                val flow = flows.firstOrNull { it.id == flowId }
                _state.value = _state.value.copy(
                    flowName = flow?.name.orEmpty(),
                    slots = flow?.slots.orEmpty(),
                )
            }
            .launchIn(scope)
    }

    fun onSlotTap(slot: SlotDescriptor) {
        val action = slot.action ?: return // placeholder — silently ignore
        scope.launch {
            val result = dispatchAction(action)
            _state.value = _state.value.copy(
                lastDispatchAction = action,
                lastDispatchResult = result,
            )
        }
    }

    /** US-508 retry: re-dispatch the most recent action. No-op if none recorded. */
    fun retryLastAction() {
        val action = _state.value.lastDispatchAction ?: return
        scope.launch {
            val result = dispatchAction(action)
            _state.value = _state.value.copy(lastDispatchResult = result)
        }
    }

    /** Called by [com.launcher.ui.screens.FlowScreen] after surfacing a result so the same snackbar is not shown twice. */
    fun acknowledgeDispatchResult() {
        _state.value = _state.value.copy(lastDispatchResult = null)
    }
}

data class FlowUiState(
    val flowName: String = "",
    val slots: List<SlotDescriptor> = emptyList(),
    val lastDispatchAction: Action? = null,
    val lastDispatchResult: DispatchResult? = null,
)
