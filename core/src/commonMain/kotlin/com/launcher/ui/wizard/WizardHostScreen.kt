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
                    val stepLabel = stringResolver.resolve(
                        key = "wizard.step_n_of_m",
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
                        val (titleKey, bodyKey) = stepKeysFor(
                            stepType = current.currentStep.stepType,
                            refId = pending.params.refId,
                        )
                        SeniorTitleText(stringResolver.resolve(titleKey))
                        Spacer(Modifier.height(12.dp))
                        SeniorBodyText(stringResolver.resolve(bodyKey))
                        Spacer(Modifier.height(24.dp))

                        // TASK-7 Phase 6 / FR-014a — walk-through mode shows
                        // semantically clearer labels:
                        //  - "Изменить" (primary) — same as Wizard "Далее":
                        //    captures the answer / triggers applyOrPrompt.
                        //  - "Оставить" (secondary) — same as Wizard "Пропустить":
                        //    Skipped, no state change.
                        // Sealed pair so future modes (e.g. ReviewOnly) can add
                        // a third label set without touching the engine.
                        val (primaryKey, secondaryKey) = when (current.mode) {
                            com.launcher.api.wizard.WizardEngine.Mode.Wizard ->
                                "wizard.next" to "wizard.skip"
                            com.launcher.api.wizard.WizardEngine.Mode.WalkThrough ->
                                "wizard.walkthrough.change" to "wizard.walkthrough.keep"
                        }
                        SeniorButton(
                            text = stringResolver.resolve(primaryKey, emptyMap()),
                            onClick = {
                                active.resolve(StepResult.AnswerCaptured(JsonPrimitive("ack")))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // In WalkThrough mode the secondary ("Оставить") is
                        // always shown — the whole point is "review and
                        // optionally skip per step". In Wizard mode we
                        // respect canSkip per FR-008.
                        val showSecondary = current.currentStep.canSkip ||
                            current.mode == com.launcher.api.wizard.WizardEngine.Mode.WalkThrough
                        if (showSecondary) {
                            Spacer(Modifier.height(12.dp))
                            SeniorSecondaryButton(
                                text = stringResolver.resolve(secondaryKey, emptyMap()),
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

/**
 * Maps (stepType, refId) → (title key, body key) following the conventions
 * baked into the bundled XML strings:
 *  - UIChoice "language" → ui.language.question / (empty)
 *  - SystemSetting "android.role.home" → system_setting.android.role.home.label / .desc
 *  - TutorialHint "first-tile-hint" → hint.first-tile-hint / (empty)
 *
 * AndroidStringResolver normalises dots to underscores at lookup time so
 * these keys hit `ui_language_question`, `system_setting_android_role_home_label`,
 * etc. in res/values{-LOCALE}/strings_wizard.xml.
 */
private fun stepKeysFor(
    stepType: com.launcher.api.wizard.StepType,
    refId: String,
): Pair<String, String> = when (stepType) {
    com.launcher.api.wizard.StepType.UIChoice ->
        "ui.$refId.question" to "ui.$refId.description"
    com.launcher.api.wizard.StepType.SystemSetting ->
        "system_setting.$refId.label" to "system_setting.$refId.desc"
    com.launcher.api.wizard.StepType.TutorialHint ->
        "hint.$refId" to "hint.$refId.body"
    is com.launcher.api.wizard.StepType.Custom ->
        "step.title.$refId" to "step.body.$refId"
}
