package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.ActionRequest
import com.launcher.api.CommunicationActionType
import com.launcher.api.CommunicationWarningCode
import com.launcher.api.DispatchResult
import com.launcher.api.FlowRepository
import com.launcher.api.SlotAction
import com.launcher.api.SlotDescriptor
import com.launcher.api.WhatsAppHandoffRequest
import com.launcher.api.WhatsAppHandoffResult
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Component for one flow's slot grid + confirmation/warning overlays.
 *
 * Until ActionDispatcher itself moves to commonMain (a later spec), it stays in
 * androidMain and is invoked through the [dispatchAction] callback the Activity
 * supplies — keeps this component platform-agnostic.
 */
class FlowComponent(
    componentContext: ComponentContext,
    val flowId: String,
    private val flowRepository: FlowRepository,
    private val dispatchAction: (ActionRequest) -> DispatchResult,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(FlowUiState())
    val state: StateFlow<FlowUiState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch {
            val flow = flowRepository.loadFlows().firstOrNull { it.id == flowId }
            _state.value = _state.value.copy(
                flowName = flow?.name.orEmpty(),
                slots = flow?.slots.orEmpty(),
            )
        }
    }

    fun onSlotTap(slot: SlotDescriptor) {
        val action = slot.action as? SlotAction.WhatsAppCall ?: return
        _state.value = _state.value.copy(
            pending = PendingAction(slot = slot, actionType = action.actionType),
            confirmationSuccess = false,
            warning = null,
        )
    }

    fun onCancel() {
        _state.value = _state.value.copy(pending = null, confirmationSuccess = false)
    }

    fun onConfirm() {
        val pending = _state.value.pending ?: return
        val whatsApp = pending.slot.action as? SlotAction.WhatsAppCall ?: return
        _state.value = _state.value.copy(confirmationSuccess = true)
        val request = WhatsAppHandoffRequest(
            tileId = pending.slot.id,
            contactRef = whatsApp.contactRef,
            actionType = pending.actionType,
            actionCycleId = randomActionCycleId(),
            homeSurfaceRef = HOME_SURFACE_REF,
        )
        val result = dispatchAction(ActionRequest.WhatsAppHandoff(request))
        if (result is DispatchResult.WhatsApp) {
            handleWhatsAppOutcome(result.outcome)
        }
    }

    private fun handleWhatsAppOutcome(outcome: WhatsAppHandoffResult) {
        when (outcome) {
            WhatsAppHandoffResult.LAUNCH_STARTED -> {
                // System will switch to WhatsApp; no UI change here.
            }
            WhatsAppHandoffResult.WHATSAPP_UNAVAILABLE -> showWarning(
                CommunicationWarningCode.WHATSAPP_UNAVAILABLE,
                "WhatsApp недоступен",
                "WhatsApp не установлен или не настроен на этом телефоне.",
            )
            WhatsAppHandoffResult.ACTION_NOT_SUPPORTED -> showWarning(
                CommunicationWarningCode.ACTION_NOT_SUPPORTED,
                "Действие недоступно",
                "Этот тип звонка пока не поддерживается этим контактом.",
            )
            else -> showWarning(
                CommunicationWarningCode.HANDOFF_LAUNCH_FAILED,
                "Не удалось открыть звонок",
                "Попробуйте ещё раз. Если не получится — обратитесь к помощнику.",
            )
        }
    }

    private fun showWarning(code: CommunicationWarningCode, title: String, message: String) {
        _state.value = _state.value.copy(
            pending = null,
            warning = WarningUiState(code = code, title = title, message = message),
        )
    }

    fun onWarningDismiss() {
        _state.value = _state.value.copy(warning = null)
    }

    private companion object {
        const val HOME_SURFACE_REF = "home_main"
        fun randomActionCycleId(): String =
            (1..32).map { Random.nextInt(16).toString(16) }.joinToString("")
    }
}

data class FlowUiState(
    val flowName: String = "",
    val slots: List<SlotDescriptor> = emptyList(),
    val pending: PendingAction? = null,
    val confirmationSuccess: Boolean = false,
    val warning: WarningUiState? = null,
)

data class PendingAction(
    val slot: SlotDescriptor,
    val actionType: CommunicationActionType,
)

data class WarningUiState(
    val code: CommunicationWarningCode,
    val title: String,
    val message: String,
)
