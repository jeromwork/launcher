package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowRepository
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
import kotlinx.serialization.Serializable

/**
 * State holder for the home screen. Loads flows once on construction; tapping a tab
 * activates a [FlowComponent] for that flow id (no router push). Settings / AddFlow
 * / Admin are pushes on the parent root stack and arrive as callbacks.
 */
class HomeComponent(
    componentContext: ComponentContext,
    private val flowRepository: FlowRepository,
    private val dispatchAction: suspend (Action) -> DispatchResult,
    val onSettingsClick: () -> Unit,
    val onAddFlowClick: () -> Unit,
    val onAdminDevicesClick: () -> Unit,
    val onAddSlotClick: (flowId: String) -> Unit,
    val onSevenTapTriggered: () -> Unit = {},
    // Spec 007 admin scanner — default no-op для совместимости с спеком 010 tests
    // которые не используют scanner. Production wiring через RootComponent.
    val onOpenScanner: () -> Unit = {},
    private val managedDevices: com.launcher.api.link.ManagedDevicesRegistry? = null,
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
                onOpenScanner = onOpenScanner,
                onAddSlotClick = { onAddSlotClick(config.flowId) },
                managedDevices = managedDevices,
            )
        },
    )

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        // Spec 010 T029 — collect `observeFlows()` as Hot Flow (ARCH-016
        // closure: HomeScreen reactively re-renders from /config/current
        // pushes; preserves the активный tab when the layout updates.
        flowRepository.observeFlows()
            .onEach { flows ->
                val previousActive = _state.value.activeFlowId
                val activeId = previousActive
                    ?.takeIf { id -> flows.any { it.id == id } }
                    ?: flows.firstOrNull()?.id
                _state.value = HomeUiState(flows = flows, activeFlowId = activeId)
                if (activeId != null && activeId != previousActive) {
                    flowSlotNav.activate(FlowSlotConfig(activeId))
                }
            }
            .launchIn(scope)
    }

    /** Spec 007 manual reload (used by RootComponent after FlowRepository.addFlow).
     *  Spec 010 observeFlows() picks up most changes automatically, но manual
     *  refresh оставлен для wizard flow где template-driven add может потребовать
     *  optimistic update. */
    fun refresh(activateFirst: Boolean = false) {
        scope.launch {
            val flows = flowRepository.loadFlows()
            val previousActive = _state.value.activeFlowId
            val newActive = when {
                activateFirst -> flows.firstOrNull()?.id
                previousActive != null && flows.any { it.id == previousActive } -> previousActive
                else -> flows.firstOrNull()?.id
            }
            _state.value = HomeUiState(flows = flows, activeFlowId = newActive)
            newActive?.let { flowSlotNav.activate(FlowSlotConfig(it)) }
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
