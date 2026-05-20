package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext

/**
 * Spec 010 T100 — Decompose component for [com.launcher.ui.gate.ChallengeGateScreen].
 * Holds only navigation callbacks; challenge state lives in the Composable via
 * `rememberSaveable` + [com.launcher.ui.gate.ChallengeSaver] per C-1.
 *
 * **No persistent counter** (FR-024 / C-2): the component does not track
 * attempts. Repeated wrong answers regenerate the challenge in the screen
 * itself; failures are not surfaced to navigation.
 */
class ChallengeGateComponent(
    componentContext: ComponentContext,
    val onSuccess: () -> Unit,
    val onCancel: () -> Unit,
) : ComponentContext by componentContext
