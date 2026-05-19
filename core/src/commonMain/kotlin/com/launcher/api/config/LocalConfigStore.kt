package com.launcher.api.config

import kotlinx.coroutines.flow.Flow

/**
 * Port for local persistence of applied-config and pending-changes (spec 008
 * §FR-041..047).
 *
 * Per CLAUDE.md §6 (mock-first):
 *  - `SqlDelightLocalConfigStore` (commonMain — KMP-pure): uses generated
 *    SQLDelight queries. Android driver injected by `AndroidSqlDriverProvider`
 *    (androidMain). iOS adapter ready для будущего ADR-001 update.
 *  - `FakeLocalConfigStore` (commonTest): in-memory map. Programmable failure
 *    injection для tests of LocalStorageCorrupt path.
 *
 * Cold-start access path (FR-044): [readAppliedConfig] is called sync-suspend
 * before first frame; SQLDelight read budget ≤ 50ms p95 per plan.md SC-004a.
 */
interface LocalConfigStore {

    /**
     * Read last-applied config for [linkId]. Returns null if no apply yet
     * for this link (cold start право after pairing).
     */
    suspend fun readAppliedConfig(linkId: String): ConfigDocument?

    /**
     * Hot Flow over the last-applied config for [linkId]. Emits the current
     * value on subscribe, then re-emits whenever [writeAppliedConfig] is
     * called for the same link (spec 010 T029 — ARCH-016 closure: HomeScreen
     * collects this as State so layout updates without explicit refresh).
     *
     * Emits `null` while no config has been applied yet for [linkId].
     */
    fun observeAppliedConfig(linkId: String): Flow<ConfigDocument?>

    /**
     * Atomic upsert of applied-config. Called by [ConfigApplier] after
     * successful remote read + local layout switch.
     */
    suspend fun writeAppliedConfig(linkId: String, config: ConfigDocument)

    /**
     * Read pending draft + snapshot для linkId. Returns null if no pending.
     */
    suspend fun readPending(linkId: String): PendingLocalChanges?

    /**
     * Upsert pending draft. Called by [ConfigEditor.updateDraft] (debounced).
     */
    suspend fun writePending(linkId: String, pending: PendingLocalChanges)

    /**
     * Remove pending entry for linkId. Called после successful push, OR by
     * user discard action (FR-057).
     */
    suspend fun clearPending(linkId: String)

    /**
     * Hot Flow of linkIds with pending. Used by Settings device-list pending
     * badge (FR-046, SC-008).
     */
    fun pendingLinks(): Flow<Set<String>>
}

/**
 * Local pending state per linkId — snapshot of `serverUpdatedAt` at the start
 * of editing session + current draft.
 *
 * Stored в SQLDelight `pending_changes` table; lives forever per FR-043.
 */
data class PendingLocalChanges(
    val linkId: String,
    val snapshotServerUpdatedAt: ServerTimestamp,
    val draftConfig: ConfigDocument,
)
