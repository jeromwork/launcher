package com.launcher.api.wizard

import kotlinx.serialization.json.JsonElement

interface WizardStep {
    val stepType: StepType
    val canSkip: Boolean
    val canGoBack: Boolean
    suspend fun execute(params: StepParams): StepResult
}

data class StepParams(
    val refId: String,
    val params: Map<String, JsonElement>,
    val stepIndex: Int,
    val totalSteps: Int,
)

sealed class StepType {
    data object UIChoice : StepType()
    data object SystemSetting : StepType()
    data object TutorialHint : StepType()
    data class Custom(val name: String) : StepType()
}

sealed class StepResult {
    data class AnswerCaptured(val answer: JsonElement) : StepResult()
    data object Skipped : StepResult()
    data object Cancelled : StepResult()
    data class BackRequested(val toStepIndex: Int) : StepResult()
}
