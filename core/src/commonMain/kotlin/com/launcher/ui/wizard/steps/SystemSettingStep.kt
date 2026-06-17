package com.launcher.ui.wizard.steps

import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.Clock
import com.launcher.api.wizard.DiagnosticEmitter
import com.launcher.api.wizard.DiagnosticEvent
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.StepType
import com.launcher.api.wizard.SystemSettingPort
import com.launcher.api.wizard.UserPreferences
import com.launcher.api.wizard.UserPreferencesStore
import com.launcher.api.wizard.AttestationRecord
import com.launcher.api.wizard.WizardStep
import kotlinx.serialization.json.JsonPrimitive

/**
 * SystemSettingStep — looks up the SystemSettingEntry by `params.refId` from
 * the bundled android-pool.json, dispatches via SystemSettingPort.
 *
 * Per FR-008, FR-008a (denial flow), FR-055.
 *
 * The Compose host renders pre-prompt, observes ApplyResult, and on Applied
 * either captures a true answer (Indeterminate strategy → SelfAttest, stored
 * in UserPreferences) or returns Skipped if user permanently denied.
 */
class SystemSettingStep(
    private val host: StepHost,
    private val systemSettingPort: SystemSettingPort,
    private val userPreferencesStore: UserPreferencesStore,
    private val diagnostics: DiagnosticEmitter,
    private val clock: Clock,
    override val canSkip: Boolean = true,
    override val canGoBack: Boolean = true,
) : WizardStep {
    override val stepType: StepType = StepType.SystemSetting

    override suspend fun execute(params: StepParams): StepResult {
        // Pre-check: if already applied, capture immediately.
        when (val status = systemSettingPort.status(params.refId)) {
            SettingStatus.Applied -> return StepResult.AnswerCaptured(JsonPrimitive("Applied"))
            SettingStatus.NotSupportedOnPlatform -> return StepResult.Skipped
            SettingStatus.Indeterminate, SettingStatus.NotApplied, is SettingStatus.CheckFailed -> {
                // Fall through to UI host to confirm with user.
            }
        }
        // Delegate to UI host for prompt / await user action.
        val uiResult = host.await(params)
        return when (uiResult) {
            is StepResult.AnswerCaptured -> {
                val applyResult = systemSettingPort.applyOrPrompt(params.refId)
                when (applyResult) {
                    ApplyResult.Applied -> StepResult.AnswerCaptured(JsonPrimitive("Applied"))
                    ApplyResult.PromptShown -> {
                        val verified = systemSettingPort.status(params.refId)
                        if (verified == SettingStatus.Applied) {
                            StepResult.AnswerCaptured(JsonPrimitive("Applied"))
                        } else if (verified == SettingStatus.Indeterminate) {
                            // SelfAttest path — record user attestation.
                            persistAttestation(params.refId, true)
                            StepResult.AnswerCaptured(JsonPrimitive("Attested"))
                        } else {
                            diagnostics.emit(DiagnosticEvent.WizardStepDenied(params.refId, isPermanent = false))
                            StepResult.Skipped
                        }
                    }
                    ApplyResult.Denied -> {
                        // User actively chose "no" — record the answer and advance.
                        // Engine MUST NOT fail just because the user declined a
                        // Required permission; wizard still finishes and the
                        // denial surfaces later as a Settings badge (S-1+).
                        diagnostics.emit(DiagnosticEvent.WizardStepDenied(params.refId, isPermanent = false))
                        StepResult.AnswerCaptured(JsonPrimitive("Denied"))
                    }
                    ApplyResult.PermanentlyDenied -> {
                        diagnostics.emit(DiagnosticEvent.WizardStepDenied(params.refId, isPermanent = true))
                        StepResult.AnswerCaptured(JsonPrimitive("PermanentlyDenied"))
                    }
                    ApplyResult.UnsupportedMechanism ->
                        StepResult.AnswerCaptured(JsonPrimitive("UnsupportedMechanism"))
                    is ApplyResult.Failed -> {
                        diagnostics.emit(DiagnosticEvent.FallbackWarning("system-setting", applyResult.reason))
                        StepResult.AnswerCaptured(JsonPrimitive("Failed: ${applyResult.reason}"))
                    }
                }
            }
            else -> uiResult
        }
    }

    private suspend fun persistAttestation(settingId: String, value: Boolean) {
        val current = userPreferencesStore.current()
        val updated: UserPreferences = current.copy(
            attestedSettings = current.attestedSettings + (
                settingId to AttestationRecord(
                    attestedAtEpochMillis = clock.nowEpochMillis(),
                    value = value,
                )
            ),
        )
        userPreferencesStore.save(updated)
    }
}
