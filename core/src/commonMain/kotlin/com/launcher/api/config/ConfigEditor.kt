package com.launcher.api.config

import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Port for editing `/config/current` on any editor device (admin-phone /
 * admin-tablet / Managed-phone — equal per FR-050).
 *
 * Two stages, **separate** per FR-040:
 *  - [updateDraft] — autosave-on-every-edit (continuous, debounced 300ms per
 *    FR-056 / [ConfigSyncConstants.AUTOSAVE_DEBOUNCE_MS]). Always succeeds
 *    (Room-level write).
 *  - [pushPending] — explicit user action «Отправить». Optimistic-concurrency
 *    check against `serverUpdatedAt`. May fail with [ConfigSyncError.Conflict]
 *    → caller shows Merge UI (FR-014).
 *
 * Pending state (between save локально и push) lives forever per FR-043 —
 * no auto-discard, no auto-push.
 *
 * Per CLAUDE.md §6:
 *  - `DefaultConfigEditor` (androidMain): uses [com.launcher.api.sync.RemoteSyncBackend]
 *    runTransaction для optimistic-concurrency check.
 *  - `FakeConfigEditor` (commonTest): wraps FakeLocalConfigStore + FakeRemoteSyncBackend
 *    с programmable conflict injection.
 */
interface ConfigEditor {

    /**
     * Apply [mutator] to current draft (or current applied if no draft yet).
     * Persists to LocalConfigStore.pending. Debounce internally.
     *
     * Continuous autosave per FR-056 — caller can invoke per keystroke;
     * implementation collapses bursts.
     */
    suspend fun updateDraft(
        linkId: String,
        mutator: (ConfigDocument) -> ConfigDocument,
    )

    /** Hot Flow of current pending draft (если есть). Used by Settings UI binding. */
    fun pendingDraft(linkId: String): Flow<ConfigDocument?>

    /**
     * Read-only snapshot of the last-applied config (spec 009 FR-001/005).
     * Admin UI uses this for the editor baseline before any local edits.
     * Returns null если apply ещё не происходил (cold start право после
     * pairing).
     *
     * Exposed here (not through [LocalConfigStore]) so UI never imports
     * the persistence port directly — spec 008 isolation gate T123.
     */
    suspend fun appliedConfig(linkId: String): ConfigDocument?

    /**
     * Spec 010 T029 — hot Flow over the last-applied config for [linkId].
     * Drives `HomeScreen` (ARCH-016 closure): emit on subscribe + re-emit
     * after every ConfigApplier write. Emits `null` while no apply has
     * happened (cold start before pairing OR cold start after pairing but
     * before first remote read).
     */
    fun observeAppliedConfig(linkId: String): Flow<ConfigDocument?>

    /**
     * Push current pending to `/config/current` с optimistic-concurrency check
     * против `clientSnapshotUpdatedAt` (FR-012, FR-013).
     *
     * On success: clears pending, server updates `serverUpdatedAt` via
     * FieldValue.serverTimestamp() (FR-002).
     * On conflict: returns [ConfigSyncError.Conflict] with fresh server state
     * + computed diff for Merge UI.
     */
    suspend fun pushPending(linkId: String): Outcome<Unit, ConfigSyncError>

    /**
     * Discard pending draft без push — irreversible. UI MUST confirm dialog
     * per FR-057.
     */
    suspend fun discardPending(linkId: String)
}
