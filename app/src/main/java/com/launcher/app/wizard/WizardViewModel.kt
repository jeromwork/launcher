package com.launcher.app.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.app.preset.task120.PresetBootstrap
import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.engine.ReconcileState
import com.launcher.preset.model.Component
import com.launcher.preset.model.Profile
import com.launcher.preset.model.ProfileComponent
import com.launcher.preset.model.RunMode
import com.launcher.preset.port.InteractionSink
import com.launcher.preset.port.ProfileStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * T051 (TASK-126 Phase 2) — bridges [ReconcileEngine] to a Compose wizard UI.
 *
 * **State machine** (see [ReconcileState]):
 *   Idle → Loading → (Interactive | Applying)* → (Done | Denied | Failed)
 *
 * **InteractionSink bridge**: [ReconcileEngine.run] is a plain suspend function returning
 * a [Profile]. The engine calls [InteractionSink.askUser] once per Interactive step; this
 * ViewModel implements the sink itself: `askUser` emits [ReconcileState.Interactive],
 * suspends on a [CompletableDeferred] until the UI calls [respond] or [deny], then resumes
 * the engine with the selected [Component] (or `null` = user cancelled).
 *
 * **NO WizardStore** — per plan.md CL-5, per-step progress is derived from each
 * `Provider.check()` on every run, not from a persisted counter. Resume after process
 * death simply re-runs the engine; already-applied components have `status == Applied`
 * and are filtered out by [ReconcileEngine.runWizard].
 *
 * **Retention**: the ViewModel is registered as a Koin `single` (matching the project's
 * existing pattern for `RecoveryViewModel` / `PairingViewModel`), so it survives
 * configuration changes trivially. On process death the flow restarts from bootstrap —
 * `ProfileStore` (DataStore-backed) preserves per-component status across processes, so
 * the engine skips already-Applied steps on re-entry (no counter drift).
 */
class WizardViewModel(
    private val bootstrap: PresetBootstrap,
    private val engine: ReconcileEngine,
    private val store: ProfileStore,
) : ViewModel(), InteractionSink {

    private val _state = MutableStateFlow<ReconcileState>(ReconcileState.Idle)
    val state: StateFlow<ReconcileState> = _state.asStateFlow()

    /** Deferred that the engine's [askUser] call is currently suspended on, if any. */
    private var pendingAnswer: CompletableDeferred<Component?>? = null

    /** Snapshot of components at start of the current run — used to compute index/total. */
    private var currentComponents: List<ProfileComponent> = emptyList()

    /**
     * Kicks off bootstrap → engine.run(Wizard). Safe to call once per ViewModel instance;
     * subsequent calls while a run is in flight are no-ops.
     */
    /**
     * Resets the state machine so [start] can be re-invoked. Used when the user
     * navigates back from a [ReconcileState.Denied] blocking screen and picks a
     * different preset.
     */
    fun reset() {
        pendingAnswer?.complete(null)
        pendingAnswer = null
        currentComponents = emptyList()
        _state.value = ReconcileState.Idle
    }

    fun start() {
        if (_state.value != ReconcileState.Idle) return
        _state.value = ReconcileState.Loading
        viewModelScope.launch {
            when (val outcome = bootstrap.bootstrap()) {
                is PresetBootstrap.BootstrapOutcome.ValidationFailed -> {
                    _state.value = ReconcileState.Failed(
                        message = outcome.i18nKeys.joinToString(","),
                    )
                    return@launch
                }
                is PresetBootstrap.BootstrapOutcome.PresetNotFound -> {
                    _state.value = ReconcileState.Failed(
                        message = "preset_not_found:${outcome.presetId}",
                    )
                    return@launch
                }
                PresetBootstrap.BootstrapOutcome.AlreadyActive,
                is PresetBootstrap.BootstrapOutcome.Activated,
                -> {
                    // fall through to engine run
                }
            }
            val profile = store.load()
            if (profile == null) {
                _state.value = ReconcileState.Failed(message = "profile_missing")
                return@launch
            }
            currentComponents = profile.components
            val finalProfile = engine.run(mode = RunMode.Wizard, sink = this@WizardViewModel)
            _state.value = ReconcileState.Done(finalProfile)
        }
    }

    /**
     * Called by the Compose layer when the user picks a [Component] value for the current
     * Interactive step. Resumes the engine.
     */
    fun respond(component: Component) {
        pendingAnswer?.complete(component)
        pendingAnswer = null
    }

    /**
     * Called by the Compose layer when the user declines the current Interactive step.
     *
     * - `required == false` → engine marks Skipped and proceeds to the next step.
     * - `required == true`  → engine also proceeds with null, but we intercept BEFORE
     *   resuming the engine to emit [ReconcileState.Denied], which the UI renders as
     *   a blocking «try preset Y» screen (T053 / FR-006 CL-6).
     *
     * The `required` flag lives on [com.launcher.preset.model.Pool.ComponentDeclaration],
     * not directly on [ProfileComponent]. For Phase 2 v1 we approximate: `required=true`
     * ≡ `ProfileComponent.critical`. Full pool lookup is a follow-up when Denial UX
     * needs richer preset-suggestion copy (currently the copy is generic).
     */
    fun deny(component: ProfileComponent) {
        if (component.critical) {
            // Emit Denied and leave the engine's askUser suspended — the UI's «try
            // preset Y» button navigates back to the preset picker, which finishes
            // this Activity (cancelling viewModelScope) and starts a fresh run.
            // Resuming with null here would let the engine proceed and overwrite
            // Denied with Done on the next terminal emit.
            _state.value = ReconcileState.Denied(component = component, required = true)
            return
        }
        // Non-critical: proceed with Skipped semantics — the engine's runWizard treats
        // `null` from askUser as ComponentStatus.Skipped and advances.
        pendingAnswer?.complete(null)
        pendingAnswer = null
    }

    // InteractionSink implementation --------------------------------------------------

    override suspend fun askUser(component: ProfileComponent): Component? {
        val idx = currentComponents.indexOfFirst { it.id == component.id }
        val total = currentComponents.size
        _state.value = ReconcileState.Interactive(
            current = component,
            index = if (idx >= 0) idx else 0,
            total = total,
        )
        val deferred = CompletableDeferred<Component?>()
        pendingAnswer = deferred
        return deferred.await()
    }
}
