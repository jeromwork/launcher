package com.launcher.ui.admin.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.admin.AdminEditorMode
import com.launcher.api.admin.EditorState
import com.launcher.api.admin.MergeConflictState
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.ConfigSnapshot
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Decompose component backing [com.launcher.ui.admin.EditorScreen] (spec 009
 * FR-001/005/010-015). Subscribes to [ConfigEditor.pendingDraft] for the
 * draft state; loads [LocalConfigStore.readAppliedConfig] on init for the
 * applied baseline; chains publish through [ConfigHistoryRepository.recordSnapshot]
 * → [ConfigEditor.pushPending] → [ConfigHistoryRepository.housekeep] per
 * plan §2 data-flow (FR-036/038, Phase D).
 *
 * State is `MutableStateFlow<EditorState>` so Compose recomposes on every
 * mutation. Activity recreation (FR-014a/b) survives because [ConfigEditor]
 * lives at `single` scope (Koin) and pending draft persists in Room.
 */
class EditorComponent(
    componentContext: ComponentContext,
    private val linkId: String,
    private val configEditor: ConfigEditor,
    private val historyRepository: ConfigHistoryRepository,
    private val selfDeviceId: String,
    private val nowMillis: () -> Long,
    val onBack: () -> Unit,
    val onHistoryClick: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(
        EditorState(
            linkId = linkId,
            mode = AdminEditorMode.View,
            draft = emptyConfig(),
            applied = null,
            pendingPush = false,
            mergeConflict = null,
        ),
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch { loadApplied() }
        scope.launch { observeDraft() }
    }

    fun toggleMode() {
        _state.update { current ->
            current.copy(
                mode = if (current.mode == AdminEditorMode.View) AdminEditorMode.Edit else AdminEditorMode.View,
            )
        }
    }

    /**
     * Apply a structural mutation to the draft and persist via
     * [ConfigEditor.updateDraft] (autosave debounced by 300 ms internally
     * per spec 008 FR-056).
     */
    fun mutateDraft(mutator: (ConfigDocument) -> ConfigDocument) {
        scope.launch {
            configEditor.updateDraft(linkId, mutator)
        }
    }

    /**
     * Publish pipeline (FR-015/036/038):
     *  1. record snapshot of current applied config (so rollback is possible);
     *  2. push pending draft → /config/current with optimistic concurrency;
     *  3. on success: housekeep (≤10 retained);
     *  4. on conflict: surface [MergeConflictState] for in-screen resolution.
     */
    fun publish() {
        val current = _state.value
        if (current.pendingPush) return
        _state.update { it.copy(pendingPush = true) }
        scope.launch {
            val applied = current.applied
            if (applied != null) {
                val snapshot = ConfigSnapshot(
                    config = applied,
                    recordedAt = nowMillis(),
                    recordedFromDeviceId = selfDeviceId,
                )
                // Best-effort — FR-037 explicitly accepts rare loss of a
                // history entry until SRV-CONFIG-001 makes the pair atomic.
                historyRepository.recordSnapshot(linkId, snapshot)
            }
            val pushResult = configEditor.pushPending(linkId)
            _state.update { it.copy(pendingPush = false) }
            when (pushResult) {
                is Outcome.Success -> {
                    historyRepository.housekeep(linkId)
                    loadApplied()
                }
                is Outcome.Failure -> when (val err = pushResult.error) {
                    is ConfigSyncError.Conflict -> {
                        _state.update {
                            it.copy(
                                mergeConflict = MergeConflictState(
                                    localDraft = it.draft,
                                    serverConfig = err.serverConfig,
                                    diff = err.localDiff,
                                    detectedAt = nowMillis(),
                                ),
                            )
                        }
                    }
                    else -> { /* Offline / Unknown — leave pending; UI shows banner via future state. */ }
                }
            }
        }
    }

    fun discardDraft() {
        scope.launch {
            configEditor.discardPending(linkId)
        }
    }

    fun dismissMergeConflict() {
        _state.update { it.copy(mergeConflict = null) }
    }

    /**
     * Rollback to a historical snapshot (FR-040). Implementation overwrites
     * the current draft with the snapshot's config; admin then taps
     * "Опубликовать" to commit. We do NOT auto-publish — explicit user
     * confirmation per Article XII Operations.
     */
    fun rollbackToSnapshot(config: ConfigDocument) {
        scope.launch {
            configEditor.updateDraft(linkId) { config }
        }
    }

    // ─── internal ─────────────────────────────────────────────────────────

    private suspend fun loadApplied() {
        val applied = configEditor.appliedConfig(linkId)
        _state.update { current ->
            current.copy(
                applied = applied,
                // If draft hasn't been initialised from a pending entry yet,
                // seed it from applied so the editor renders something
                // meaningful on cold start.
                draft = current.draft.takeIf { it.flows.isNotEmpty() || it.contacts.isNotEmpty() }
                    ?: applied
                    ?: emptyConfig(),
            )
        }
    }

    private suspend fun observeDraft() {
        configEditor.pendingDraft(linkId).collectLatest { draft ->
            if (draft != null) {
                _state.update { it.copy(draft = draft) }
            }
        }
    }

    private fun emptyConfig(): ConfigDocument = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 0, nanoseconds = 0),
        lastWriterDeviceId = selfDeviceId,
        presetId = "",
        flows = emptyList(),
        contacts = emptyList(),
    )
}
