package com.launcher.ui.admin.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.config.ConfigDocument
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.history.ConfigSnapshotWithId
import com.launcher.api.history.RepositoryError
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
 * Decompose component backing [com.launcher.ui.admin.HistoryScreen]
 * (spec 009 FR-037..040).
 *
 * `readAll` is one-shot — history grows only on publish, so the snapshot
 * list refreshes on screen open (and after a rollback that itself
 * records a new entry through [EditorComponent.publish]).
 *
 * Rollback flow: caller passes the chosen [ConfigSnapshotWithId] back to
 * [EditorComponent] via [onRollback]. [EditorComponent.rollbackToSnapshot]
 * seeds the draft; admin then taps "Опубликовать" to commit (explicit
 * user confirmation per Article XII Operations).
 */
class HistoryComponent(
    componentContext: ComponentContext,
    private val linkId: String,
    private val historyRepository: ConfigHistoryRepository,
    val rollbackAllowed: Boolean,
    val onBack: () -> Unit,
    val onRollback: (ConfigDocument) -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = historyRepository.readAll(linkId)) {
                is Outcome.Success -> _state.update {
                    it.copy(loading = false, snapshots = r.value, error = null)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = r.error)
                }
            }
        }
    }

    fun rollback(snapshot: ConfigSnapshotWithId) {
        onRollback(snapshot.snapshot.config)
    }
}

data class HistoryUiState(
    val loading: Boolean = true,
    val snapshots: List<ConfigSnapshotWithId> = emptyList(),
    val error: RepositoryError? = null,
)
