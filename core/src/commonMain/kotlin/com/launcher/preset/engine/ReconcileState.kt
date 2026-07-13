package com.launcher.preset.engine

import com.launcher.preset.model.Profile
import com.launcher.preset.model.ProfileComponent

/**
 * Observable state emitted by the wizard runtime while `ReconcileEngine.run(RunMode.Wizard)`
 * walks the Profile (T051 / FR-008). The engine itself remains a plain suspend function
 * returning a `Profile`; this sealed class lives one layer up in the ViewModel that
 * bridges the engine to a Compose UI.
 *
 * The state machine per plan.md §Phase 2:
 *   Idle → Loading → (Interactive | Applying)* → (Done | Denied | Failed)
 *
 * `Interactive` is the only state that suspends waiting for user input — the ViewModel
 * emits it, blocks inside `InteractionSink.askUser()` on a `CompletableDeferred<Component?>`,
 * and resumes once the UI calls `WizardViewModel.respond(...)`.
 */
sealed class ReconcileState {
    object Idle : ReconcileState()
    object Loading : ReconcileState()

    /** Engine paused on an interactive component; UI collects and calls `respond()`. */
    data class Interactive(
        val current: ProfileComponent,
        val index: Int,
        val total: Int,
    ) : ReconcileState()

    /** Provider.apply() in flight for `current`; UI can render a progress spinner. */
    data class Applying(
        val current: ProfileComponent,
        val index: Int,
        val total: Int,
    ) : ReconcileState()

    /**
     * User declined a `required=true` step (T053 / FR-006 CL-6). The UI shows a blocking
     * screen with copy «This preset requires X. Try preset Y where it is not needed» —
     * never «reinstall». `required=false` denials do NOT reach this state; the engine
     * marks the step Skipped and proceeds.
     */
    data class Denied(
        val component: ProfileComponent,
        val required: Boolean,
    ) : ReconcileState()

    /** All components resolved (Applied / Skipped / Failed). Terminal. */
    data class Done(val profile: Profile) : ReconcileState()

    /** Bootstrap validation or engine failure. Terminal. */
    data class Failed(val message: String) : ReconcileState()
}
