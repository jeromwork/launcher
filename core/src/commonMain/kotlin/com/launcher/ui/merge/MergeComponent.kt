package com.launcher.ui.merge

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.config.ConfigDiff
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ElementId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decompose component для MergeScreen (spec 008 Phase 9 T110, FR-050).
 *
 * Owns:
 *  - The [ConfigDiff] computed at conflict time;
 *  - The user's [MergeChoiceSet] (mutable as user picks);
 *  - Save/cancel callbacks delegating к caller (ConfigEditor).
 *
 * State survival on Activity recreation: handled by Decompose ChildStack
 * Config serialization at the parent component level (per state-management
 * checklist CHK008/009). For now choices reset on recreation — full
 * rememberSaveable wiring deferred to integration task в app/.
 */
class MergeComponent(
    componentContext: ComponentContext,
    val localConfig: ConfigDocument,
    val serverConfig: ConfigDocument,
    val diff: ConfigDiff,
    private val onSaveChoices: (MergeChoiceSet) -> Unit,
    private val onCancelled: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(
        MergeUiState(
            diff = diff,
            choices = MergeDefaults.forDiff(diff),
            isAutoMergeable = !diff.hasOverlappingChanges,
            canSave = MergeDefaults.areAllChoicesMade(diff, MergeChoiceSet()),
        )
    )
    val state: StateFlow<MergeUiState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    fun pickFlowChoice(id: ElementId, choice: MergeChoice) {
        val current = _state.value.choices
        val updated = current.copy(flowChoices = current.flowChoices + (id to choice))
        updateChoices(updated)
    }

    fun pickContactChoice(id: ElementId, choice: MergeChoice) {
        val current = _state.value.choices
        val updated = current.copy(contactChoices = current.contactChoices + (id to choice))
        updateChoices(updated)
    }

    fun pickPresetChoice(choice: MergeChoice) {
        val current = _state.value.choices
        val updated = current.copy(presetChoice = choice)
        updateChoices(updated)
    }

    fun onSave() {
        if (!_state.value.canSave) return // Defensive: button должно быть disabled.
        onSaveChoices(_state.value.choices)
    }

    fun onCancel() {
        // FR-055: cancel preserves pending — no Local-storage mutation here.
        onCancelled()
    }

    private fun updateChoices(updated: MergeChoiceSet) {
        _state.value = _state.value.copy(
            choices = updated,
            canSave = MergeDefaults.areAllChoicesMade(diff, updated),
        )
    }
}

data class MergeUiState(
    val diff: ConfigDiff,
    val choices: MergeChoiceSet,
    val isAutoMergeable: Boolean,
    val canSave: Boolean,
)
