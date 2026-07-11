package com.launcher.app.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.launcher.api.localization.StringResolver
import com.launcher.preset.engine.ReconcileState
import com.launcher.preset.model.Component
import com.launcher.preset.model.Profile
import com.launcher.preset.model.ProfileComponent
import com.launcher.ui.senior.primitives.SeniorButton
import com.launcher.ui.senior.primitives.SeniorSecondaryButton
import com.launcher.ui.senior.progress.WizardProgressIndicator

/**
 * T052 (TASK-126 Phase 2) — Compose UI for the new preset-composition wizard.
 *
 * Collects [WizardViewModel.state] and renders one screen per [ReconcileState]
 * branch. Non-interactive branches (Loading, Applying) show a spinner; the only
 * user interaction happens inside [ReconcileState.Interactive], which resumes
 * the engine via [WizardViewModel.respond] / [WizardViewModel.deny].
 *
 * Denial UX (T053 / FR-006 CL-6): when the user declines a `critical=true`
 * component, the engine is NOT resumed. [ReconcileState.Denied] renders a
 * blocking screen with copy "This preset requires X. Try preset Y where it
 * is not needed" — never "reinstall". The "try preset Y" button invokes
 * [onPickAnotherPreset], which the host activity uses to navigate back to
 * the preset picker.
 *
 * Per-Component UI (Interactive step body) is intentionally generic in this
 * commit: label + Confirm/Skip. Component-specific renderers (LauncherRole
 * system dialog trigger, Theme preview, Language chooser, etc.) are added
 * during Xiaomi smoke iteration (T063) — they don't change the state-machine
 * contract this screen implements.
 */
@Composable
fun WizardScreen(
    viewModel: WizardViewModel,
    stringResolver: StringResolver,
    onCompleted: (Profile) -> Unit,
    onPickAnotherPreset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(state) {
        if (state is ReconcileState.Done) {
            onCompleted((state as ReconcileState.Done).profile)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            ReconcileState.Idle,
            ReconcileState.Loading -> LoadingBody(stringResolver)

            is ReconcileState.Interactive -> InteractiveBody(
                step = s,
                stringResolver = stringResolver,
                onConfirm = { viewModel.respond(it) },
                onSkip = { viewModel.deny(s.current) },
            )

            is ReconcileState.Applying -> ApplyingBody(
                step = s,
                stringResolver = stringResolver,
            )

            is ReconcileState.Denied -> DeniedBody(
                stringResolver = stringResolver,
                component = s.component,
                onPickAnotherPreset = onPickAnotherPreset,
            )

            is ReconcileState.Done -> LoadingBody(stringResolver) // transient — routed by LaunchedEffect

            is ReconcileState.Failed -> FailedBody(
                stringResolver = stringResolver,
                message = s.message,
            )
        }
    }
}

@Composable
private fun LoadingBody(stringResolver: StringResolver) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResolver.resolve("wizard_loading"),
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun InteractiveBody(
    step: ReconcileState.Interactive,
    stringResolver: StringResolver,
    onConfirm: (Component) -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        WizardProgressIndicator(
            stepIndex = step.index,
            totalSteps = step.total.coerceAtLeast(1),
            stepLabel = stringResolver.resolve(
                "wizard_step_of",
                mapOf(
                    "current" to (step.index + 1).toString(),
                    "total" to step.total.coerceAtLeast(1).toString(),
                ),
            ),
        )
        Text(
            text = componentLabel(stringResolver, step.current),
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )
        SeniorButton(
            text = stringResolver.resolve("wizard_confirm"),
            onClick = { onConfirm(step.current.component) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!step.current.critical) {
            SeniorSecondaryButton(
                text = stringResolver.resolve("wizard_skip"),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ApplyingBody(
    step: ReconcileState.Applying,
    stringResolver: StringResolver,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResolver.resolve("wizard_applying"),
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = componentLabel(stringResolver, step.current),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun DeniedBody(
    stringResolver: StringResolver,
    component: ProfileComponent,
    onPickAnotherPreset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResolver.resolve(
                "wizard_denied_required_title",
                mapOf("component" to componentLabel(stringResolver, component)),
            ),
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResolver.resolve("wizard_denied_required_body"),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        SeniorButton(
            text = stringResolver.resolve("wizard_pick_another_preset"),
            onClick = onPickAnotherPreset,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FailedBody(
    stringResolver: StringResolver,
    message: String,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResolver.resolve("wizard_failed_title"),
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Resolves a human-readable label for a component step. Generic v1: uses the
 * component id as a translation key with a fallback to the component subtype
 * simple name. Component-specific renderers land during Xiaomi smoke (T063).
 */
private fun componentLabel(stringResolver: StringResolver, pc: ProfileComponent): String {
    val labelKey = "wizard_component_${pc.id}"
    val resolved = stringResolver.resolve(labelKey)
    if (resolved != labelKey) return resolved
    return when (val c = pc.component) {
        is Component.AppTile -> stringResolver.resolve(c.labelKey)
        is Component.FontSize -> stringResolver.resolve("wizard_component_font_size")
        is Component.Sos -> stringResolver.resolve("wizard_component_sos")
        is Component.Toolbar -> stringResolver.resolve("wizard_component_toolbar")
        Component.LauncherRole -> stringResolver.resolve("wizard_component_launcher_role")
        is Component.Theme -> stringResolver.resolve("wizard_component_theme")
        is Component.Language -> stringResolver.resolve("wizard_component_language")
        Component.StatusBarPolicy -> stringResolver.resolve("wizard_component_status_bar_policy")
    }
}
