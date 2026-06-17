package com.launcher.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.localization.StringResolver
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.WizardEngine
import com.launcher.api.wizard.WizardState
import com.launcher.ui.senior.primitives.SeniorButton
import com.launcher.ui.senior.primitives.SeniorBodyText
import com.launcher.ui.senior.primitives.SeniorSecondaryButton
import com.launcher.ui.senior.primitives.SeniorTitleText
import com.launcher.ui.senior.progress.LiveRegionAnnouncement
import com.launcher.ui.senior.progress.WizardProgressIndicator
import com.launcher.ui.wizard.steps.StepHost
import kotlinx.serialization.json.JsonPrimitive

/**
 * Top-level wizard Composable.
 *
 * Observes [WizardEngine.currentState] and renders the active step. Each
 * step type binds to its own [StepHost], which the host screen submits
 * answers to.
 *
 * Per FR-008a (denial flow), FR-008b (live region), FR-008c (visual progress),
 * FR-008d (Back behaviour).
 */
@Composable
fun WizardHostScreen(
    engine: WizardEngine,
    stringResolver: StringResolver,
    uiChoiceHost: StepHost,
    systemSettingHost: StepHost,
    tutorialHintHost: StepHost,
    onCompleted: () -> Unit,
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by engine.currentState().collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val current = state) {
            is WizardState.Idle -> {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SeniorBodyText(stringResolver.resolve("wizard.loading", emptyMap()))
                }
            }

            is WizardState.Completed -> {
                onCompleted()
            }

            is WizardState.Running -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    val stepLabel = stringResolver.resolvePlural(
                        key = "wizard.step_n_of_m",
                        count = current.totalSteps,
                        args = mapOf(
                            "current" to (current.currentStepIndex + 1),
                            "total" to current.totalSteps,
                        ),
                    )
                    WizardProgressIndicator(
                        stepIndex = current.currentStepIndex,
                        totalSteps = current.totalSteps,
                        stepLabel = stepLabel,
                    )
                    LiveRegionAnnouncement(text = stepLabel)
                    Spacer(Modifier.height(16.dp))

                    val active = activeHost(
                        stepType = current.currentStep.stepType,
                        uiChoiceHost = uiChoiceHost,
                        systemSettingHost = systemSettingHost,
                        tutorialHintHost = tutorialHintHost,
                    )

                    val pending = active.pending
                    if (pending != null) {
                        SeniorTitleText(
                            stringResolver.resolve(
                                key = "step.title.${pending.params.refId}",
                                args = emptyMap(),
                            ),
                        )
                        Spacer(Modifier.height(12.dp))
                        SeniorBodyText(
                            stringResolver.resolve(
                                key = "step.body.${pending.params.refId}",
                                args = emptyMap(),
                            ),
                        )
                        Spacer(Modifier.height(24.dp))

                        // Two buttons: primary "Далее" captures a no-op answer
                        // (per FR-008 — UI variants override this default by
                        // providing a richer host binding); secondary "Пропустить".
                        SeniorButton(
                            text = stringResolver.resolve("wizard.next", emptyMap()),
                            onClick = {
                                active.resolve(StepResult.AnswerCaptured(JsonPrimitive("ack")))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (current.currentStep.canSkip) {
                            Spacer(Modifier.height(12.dp))
                            SeniorSecondaryButton(
                                text = stringResolver.resolve("wizard.skip", emptyMap()),
                                onClick = { active.resolve(StepResult.Skipped) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun activeHost(
    stepType: com.launcher.api.wizard.StepType,
    uiChoiceHost: StepHost,
    systemSettingHost: StepHost,
    tutorialHintHost: StepHost,
): StepHost = when (stepType) {
    com.launcher.api.wizard.StepType.UIChoice -> uiChoiceHost
    com.launcher.api.wizard.StepType.SystemSetting -> systemSettingHost
    com.launcher.api.wizard.StepType.TutorialHint -> tutorialHintHost
    is com.launcher.api.wizard.StepType.Custom -> uiChoiceHost
}
