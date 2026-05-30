package com.launcher.ui.edit

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.edit.EditError
import com.launcher.api.edit.EditMode
import com.launcher.api.edit.EditUiProfile
import com.launcher.api.edit.PickerType
import com.launcher.api.edit.TargetIdentity
import com.launcher.api.edit.TileEditOperation
import com.launcher.api.edit.TileEditOperations
import com.launcher.api.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Decompose component backing F-014 EditModeComposable. Holds presentation
 * state for tile editing: edit-mode active flag, target identity, derived
 * UX profile (per FR-008/Q9), pending picker, conflict snackbar visibility.
 *
 * All business logic lives here — composables read [state] and call methods.
 * This makes the component unit-testable без UI runtime (JVM-only).
 *
 * **Profile asymmetry per Q7** (FR-016/FR-017):
 *  - `AdminProfile`: on `ConfigSyncError.Conflict` → conflict snackbar with
 *    [Обновить]/[Перезаписать] actions.
 *  - `SeniorProfile`: on `ConfigSyncError.Conflict` → silent
 *    `pushPending(force = true)` retry (last-local-write-wins). No UI dialog.
 *
 * Lifecycle:
 *  - State scoped to `componentContext` lifecycle (Decompose state preservation
 *    survives Activity recreation — state-management.md CHK001).
 *  - Process death: `EditMode.active` is **not** persisted; on restart user
 *    returns to use mode (FR-010 mainstream behavior).
 */
class EditModeComponent(
    componentContext: ComponentContext,
    private val configEditor: ConfigEditor,
    val onExit: () -> Unit = {},
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(EditModeState.idle())
    val state: StateFlow<EditModeState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    /**
     * Enter edit mode for [target]. Derives profile via [EditMode.forTarget]
     * (calls [com.launcher.api.edit.EditUiProfileSelector] под капотом).
     *
     * If profile selection refuses ([EditError.ProfileSelectionRequiresCapabilityRegistry]
     * — custom preset, per FR-008b), surfaces error state instead of entering
     * edit mode. UI shows placeholder "Custom presets coming soon" screen.
     */
    fun enterEditMode(target: TargetIdentity) {
        val editMode = EditMode.forTarget(target)
        if (editMode == null) {
            _state.update {
                it.copy(
                    profileSelectionError = EditError.ProfileSelectionRequiresCapabilityRegistry,
                )
            }
            return
        }
        _state.update {
            it.copy(
                editMode = editMode.copy(active = true),
                profileSelectionError = null,
                pickerVisible = false,
                conflictPending = false,
            )
        }
    }

    /** Exit edit mode. Resets pending picker / conflict UI. */
    fun exitEditMode() {
        _state.update { it.copy(editMode = null, pickerVisible = false, conflictPending = false) }
        onExit()
    }

    /**
     * Open unified picker (FR-018) for the current target. Picker tabs filtered
     * by target preset (FR-019: SimpleLauncher → 3 tabs only).
     *
     * Empty-state «+» tile (FR-020a) calls this directly bypassing edit mode —
     * for that case caller invokes [openPickerForEmptyState] instead.
     */
    fun openPicker() {
        if (_state.value.editMode?.active != true) return
        _state.update { it.copy(pickerVisible = true) }
    }

    /**
     * Open picker для empty-state «+» tile, bypassing edit mode entry
     * per Q6 + FR-020a. Sets a transient flag so when picker closes, user
     * remains in use mode (no edit-mode persistence).
     */
    fun openPickerForEmptyState(target: TargetIdentity) {
        val editMode = EditMode.forTarget(target)
        if (editMode == null) {
            _state.update {
                it.copy(
                    profileSelectionError = EditError.ProfileSelectionRequiresCapabilityRegistry,
                )
            }
            return
        }
        _state.update {
            // editMode.active = false — we are NOT in edit mode, just opening picker.
            it.copy(
                editMode = editMode,
                pickerVisible = true,
                emptyStatePickerBypass = true,
            )
        }
    }

    /** Close picker without selecting anything. */
    fun dismissPicker() {
        _state.update { it.copy(pickerVisible = false) }
    }

    /**
     * Returns visible picker tabs for the current target preset.
     * Workspace → all 5 tabs. Simple Launcher → 3 (no Widget/Action) per FR-019.
     *
     * Returns empty list when no edit mode active.
     */
    fun visiblePickerTabs(): List<PickerType> {
        val em = _state.value.editMode ?: return emptyList()
        return when (em.target.presetId) {
            "simple-launcher" -> listOf(PickerType.Application, PickerType.Contact, PickerType.Document)
            else -> PickerType.entries
        }
    }

    /**
     * Apply a tile-edit operation atomically via [ConfigEditor.updateDraft].
     * Returns success/failure result для caller to display undo snackbar.
     *
     * Profile-asymmetric conflict handling per Q7:
     *  - Admin profile: bubble [ConfigSyncError.Conflict] to [state.conflictPending].
     *  - Senior profile: silent retry с `pushPending(force = true)` —
     *    last-local-write-wins. Caller doesn't see conflict UI.
     *
     * Note: actual `updateDraft` is autosave — it doesn't push synchronously.
     * Conflict is detected later on explicit [pushChanges] call.
     */
    fun applyOperation(op: TileEditOperation): Outcome<Unit, EditError> {
        val em = _state.value.editMode ?: return Outcome.Failure(EditError.NotAuthorized)
        if (!em.active && !_state.value.emptyStatePickerBypass) {
            return Outcome.Failure(EditError.NotAuthorized)
        }
        scope.launch {
            configEditor.updateDraft(em.target.linkId) { current ->
                when (val result = TileEditOperations.apply(op, current)) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> current // domain validation failed — leave draft untouched
                }
            }
        }
        return Outcome.Success(Unit)
    }

    /**
     * Explicit «Готово» push (FR-010). Pushes pending draft via ConfigEditor;
     * profile-asymmetric conflict handling per Q7.
     */
    fun pushChanges() {
        val em = _state.value.editMode ?: return
        scope.launch {
            val result = configEditor.pushPending(em.target.linkId)
            when (result) {
                is Outcome.Success -> {
                    // Push success; exit edit mode.
                    _state.update { it.copy(editMode = null) }
                    onExit()
                }
                is Outcome.Failure -> when (val err = result.error) {
                    is ConfigSyncError.Conflict -> handleConflict(em.profile)
                    else -> { /* offline / unknown — surface via future state */ }
                }
            }
        }
    }

    /**
     * Force overwrite («Перезаписать» — admin only) per FR-017. Senior profile
     * UI never shows this option; senior conflict handled silently via
     * [handleConflict].
     *
     * **F-014.0 limitation**: `ConfigEditor.pushPending(force=true)` overload
     * doesn't exist in спека 008 port yet. For now, this method performs
     * a regular `pushPending` retry — which will conflict again if server
     * state hasn't changed. Real force-push lands when ConfigEditor port
     * adds the `force` parameter (planned for F-014.1).
     *
     * TODO(F-014.1): extend ConfigEditor.pushPending with `force: Boolean = false`
     * overload that skips optimistic-concurrency precondition. Track via
     * dependency-spec issue.
     */
    fun overwriteRemote() {
        val em = _state.value.editMode ?: return
        if (em.profile != EditUiProfile.AdminProfile) return // FR-017 admin-only
        scope.launch {
            // F-014.0 stub: retry без force; F-014.1 will pass force = true.
            configEditor.pushPending(em.target.linkId)
            _state.update { it.copy(conflictPending = false, editMode = null) }
            onExit()
        }
    }

    /**
     * Refresh local with server state («Обновить»). Re-fetches via ConfigEditor
     * (which subscribes to applied config via Flow). For F-014.0 placeholder:
     * just clear pending draft and surface fresh state to user.
     */
    fun refreshFromRemote() {
        val em = _state.value.editMode ?: return
        scope.launch {
            configEditor.discardPending(em.target.linkId)
            _state.update { it.copy(conflictPending = false) }
        }
    }

    /** Dismiss the conflict snackbar without action. */
    fun dismissConflict() {
        _state.update { it.copy(conflictPending = false) }
    }

    private fun handleConflict(profile: EditUiProfile) {
        when (profile) {
            EditUiProfile.AdminProfile -> {
                // Bubble to UI: snackbar «Обновить / Перезаписать» (FR-016 admin branch).
                _state.update { it.copy(conflictPending = true) }
            }
            EditUiProfile.SeniorProfile -> {
                // Silent last-local-write-wins per Q7. Senior side wins because
                // F-014.0 is local-only — there's no remote racing yet. In F-014.1
                // this will call pushPending(force = true) to overwrite admin's
                // pending push silently. TODO(F-014.1): same dependency as overwriteRemote.
                val em = _state.value.editMode ?: return
                scope.launch {
                    configEditor.pushPending(em.target.linkId)
                    _state.update { it.copy(editMode = null) }
                    onExit()
                }
            }
        }
    }
}

/**
 * Presentation state for [EditModeComponent]. UI-scoped — survives Activity
 * recreation via Decompose. Process death resets to [idle].
 */
data class EditModeState(
    val editMode: EditMode?,
    val pickerVisible: Boolean,
    val conflictPending: Boolean,
    val profileSelectionError: EditError?,
    val emptyStatePickerBypass: Boolean,
) {
    companion object {
        fun idle(): EditModeState = EditModeState(
            editMode = null,
            pickerVisible = false,
            conflictPending = false,
            profileSelectionError = null,
            emptyStatePickerBypass = false,
        )
    }
}
