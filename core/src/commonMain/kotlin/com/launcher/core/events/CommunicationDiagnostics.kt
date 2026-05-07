package com.launcher.core.events

import com.launcher.api.CommunicationActionType
import com.launcher.api.CommunicationDiagnosticEventType
import com.launcher.api.ProjectEvent

class CommunicationDiagnostics(
    private val emitEvent: ((ProjectEvent) -> Unit)? = null,
) {
    fun launchConfirmed(tileRef: String, actionType: CommunicationActionType, cycleRef: String) {
        emit(
            CommunicationDiagnosticEventType.WHATSAPP_LAUNCH_CONFIRMED,
            tileRef = tileRef,
            actionType = actionType,
            cycleRef = cycleRef,
        )
    }

    fun launchFailed(
        tileRef: String?,
        actionType: CommunicationActionType?,
        cycleRef: String?,
        reasonCode: String,
    ) {
        emit(
            CommunicationDiagnosticEventType.WHATSAPP_LAUNCH_FAILED,
            tileRef = tileRef,
            actionType = actionType,
            cycleRef = cycleRef,
            reasonCode = reasonCode,
        )
    }

    fun restoreSuccess(cycleRef: String?) {
        emit(CommunicationDiagnosticEventType.RETURN_RESTORE_SUCCESS, cycleRef = cycleRef)
    }

    fun restoreFallback(cycleRef: String?, reasonCode: String) {
        emit(
            CommunicationDiagnosticEventType.RETURN_RESTORE_FALLBACK,
            cycleRef = cycleRef,
            reasonCode = reasonCode,
        )
    }

    fun configInvalid(contactRef: String?, actionType: CommunicationActionType?, reasonCode: String) {
        emit(
            CommunicationDiagnosticEventType.CONFIG_INVALID_OR_CAPABILITY_FAILED,
            tileRef = contactRef,
            actionType = actionType,
            reasonCode = reasonCode,
        )
    }

    private fun emit(
        type: CommunicationDiagnosticEventType,
        tileRef: String? = null,
        actionType: CommunicationActionType? = null,
        cycleRef: String? = null,
        reasonCode: String? = null,
    ) {
        emitEvent?.invoke(
            ProjectEvent.CommunicationDiagnostic(
                eventType = type,
                actionCycleRef = cycleRef,
                tileRef = tileRef,
                actionType = actionType,
                reasonCode = reasonCode,
            ),
        )
    }
}

