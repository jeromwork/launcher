package com.launcher.core.events

import com.launcher.api.CapabilityState
import com.launcher.api.ControlMode
import com.launcher.api.EscapeVector
import com.launcher.api.ProjectEvent
import com.launcher.api.SafetyCapability
import com.launcher.api.SafetyDiagnosticEventType

class SafetyDiagnostics(
    private val emitEvent: ((ProjectEvent) -> Unit)? = null,
) {
    fun escapeAttempt(vector: EscapeVector, mode: ControlMode, reasonCode: String? = null) {
        emit(
            eventType = SafetyDiagnosticEventType.ESCAPE_ATTEMPT_DETECTED,
            vector = vector,
            controlMode = mode,
            reasonCode = reasonCode,
        )
    }

    fun escapeBlocked(vector: EscapeVector, mode: ControlMode, reasonCode: String? = null) {
        emit(
            eventType = SafetyDiagnosticEventType.ESCAPE_BLOCKED,
            vector = vector,
            controlMode = mode,
            reasonCode = reasonCode,
        )
    }

    fun escapeRecoveredToHome(vector: EscapeVector, mode: ControlMode, reasonCode: String? = null) {
        emit(
            eventType = SafetyDiagnosticEventType.ESCAPE_RECOVERED_TO_HOME,
            vector = vector,
            controlMode = mode,
            reasonCode = reasonCode,
        )
    }

    fun capabilityMissing(
        capability: SafetyCapability,
        mode: ControlMode,
        state: CapabilityState,
        reasonCode: String? = null,
    ) {
        if (state == CapabilityState.GRANTED) {
            return
        }
        emit(
            eventType = SafetyDiagnosticEventType.CAPABILITY_MISSING,
            controlMode = mode,
            capability = capability,
            reasonCode = reasonCode ?: state.name.lowercase(),
        )
    }

    private fun emit(
        eventType: SafetyDiagnosticEventType,
        vector: EscapeVector? = null,
        controlMode: ControlMode? = null,
        capability: SafetyCapability? = null,
        reasonCode: String? = null,
    ) {
        emitEvent?.invoke(
            ProjectEvent.SafetyDiagnostic(
                eventType = eventType,
                vector = vector,
                controlMode = controlMode,
                capability = capability,
                reasonCode = reasonCode,
            ),
        )
    }
}

