package com.launcher.api.wizard

/**
 * Diagnostic port (per A-17). No real implementation in F-3 — analytics
 * backend ships in S-1+. Fake adapter in commonTest records events for
 * assertions.
 */
interface DiagnosticEmitter {
    fun emit(event: DiagnosticEvent)
}

sealed class DiagnosticEvent {
    data class WizardStarted(val manifestId: String) : DiagnosticEvent()
    data class WizardStepCompleted(val stepIndex: Int, val stepType: String) : DiagnosticEvent()
    data class WizardCompleted(val manifestId: String) : DiagnosticEvent()
    data class WizardCancelled(val atStep: Int) : DiagnosticEvent()
    data class WizardStepDenied(val settingId: String, val isPermanent: Boolean) : DiagnosticEvent()
    data class FallbackWarning(val area: String, val reason: String) : DiagnosticEvent()
}
