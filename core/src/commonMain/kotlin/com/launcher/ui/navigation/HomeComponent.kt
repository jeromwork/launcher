package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
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

/**
 * State holder for the home screen. Loads flows once on construction; tapping a tab
 * just switches the active flow id (no router push). Settings / AddFlow / Admin are
 * pushes on the parent stack and arrive as callbacks.
 */
class HomeComponent(
    componentContext: ComponentContext,
    private val flowRepository: FlowRepository,
    val onSettingsClick: () -> Unit,
    val onAddFlowClick: () -> Unit,
    val onAdminDevicesClick: () -> Unit,
    val onAddSlotClick: (flowId: String) -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch {
            val flows = flowRepository.loadFlows()
            _state.value = HomeUiState(
                flows = flows,
                activeFlowId = flows.firstOrNull()?.id,
            )
        }
    }

    fun selectFlow(flowId: String) {
        _state.value = _state.value.copy(activeFlowId = flowId)
    }
}

/**
 * UI state for [HomeComponent]. Kept tiny on purpose; debug status lines from the
 * old XML home (profile-id, catalog-generation) are not part of the production UI
 * and can be reintroduced behind a debug flag if needed.
 */
data class HomeUiState(
    val flows: List<FlowDescriptor> = emptyList(),
    val activeFlowId: String? = null,
)
