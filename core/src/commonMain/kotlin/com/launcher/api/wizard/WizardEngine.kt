package com.launcher.api.wizard

import com.launcher.api.wizard.data.WizardManifest
import kotlinx.coroutines.flow.StateFlow

interface WizardEngine {
    suspend fun run(manifest: WizardManifest): WizardOutcome
    fun currentState(): StateFlow<WizardState>
    suspend fun diffPending(
        savedCompletedManifest: WizardManifest?,
        currentManifest: WizardManifest,
    ): List<PendingStep>
}
