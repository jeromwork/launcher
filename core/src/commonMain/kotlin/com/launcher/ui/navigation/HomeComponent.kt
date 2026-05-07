package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.ActionRequest
import com.launcher.api.DispatchResult
import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * State holder for the home screen. Loads flows once on construction; tapping a tab
 * activates a [FlowComponent] for that flow id (no router push). Settings / AddFlow
 * / Admin are pushes on the parent root stack and arrive as callbacks.
 */
class HomeComponent(
    componentContext: ComponentContext,
    private val flowRepository: FlowRepository,
    private val dispatchAction: (ActionRequest) -> DispatchResult,
    val onSettingsClick: () -> Unit,
    val onAddFlowClick: () -> Unit,
    val onAdminDevicesClick: () -> Unit,
    val onAddSlotClick: (flowId: String) -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val flowSlotNav = SlotNavigation<FlowSlotConfig>()
    val flowSlot: Value<ChildSlot<FlowSlotConfig, FlowComponent>> = childSlot(
        source = flowSlotNav,
        serializer = FlowSlotConfig.serializer(),
        handleBackButton = false,
        childFactory = { config, ctx ->
            FlowComponent(
                componentContext = ctx,
                flowId = config.flowId,
                flowRepository = flowRepository,
                dispatchAction = dispatchAction,
            )
        },
    )

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch {
            val flows = flowRepository.loadFlows()
            val firstId = flows.firstOrNull()?.id
            _state.value = HomeUiState(flows = flows, activeFlowId = firstId)
            firstId?.let { flowSlotNav.activate(FlowSlotConfig(it)) }
        }
    }

    fun selectFlow(flowId: String) {
        if (_state.value.activeFlowId == flowId) return
        _state.value = _state.value.copy(activeFlowId = flowId)
        flowSlotNav.activate(FlowSlotConfig(flowId))
    }

    @Serializable
    data class FlowSlotConfig(val flowId: String)
}

/**
 * UI state for [HomeComponent]. Flow grid + overlays themselves live in the
 * active [FlowComponent] surfaced through [HomeComponent.flowSlot].
 */
data class HomeUiState(
    val flows: List<FlowDescriptor> = emptyList(),
    val activeFlowId: String? = null,
)
