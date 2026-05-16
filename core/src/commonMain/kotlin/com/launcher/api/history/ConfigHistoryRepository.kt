package com.launcher.api.history

import com.launcher.api.config.ConfigSnapshot
import com.launcher.api.result.Outcome

/**
 * Port owning the `/links/{linkId}/config/history/{autoId}` subcollection
 * (spec 009 FR-036..FR-040). Real adapter (`FirestoreConfigHistoryAdapter`
 * in `androidMain`) wraps Firestore `CollectionReference`; fake adapter
 * (`InMemoryConfigHistoryRepository` in test sources) uses sorted
 * in-memory list.
 *
 * Anti-spoof note: `recordedFromDeviceId` is enforced server-side equal to
 * `request.auth.uid` (FR-045a). Clients must populate it from their auth
 * uid; mismatch causes Firestore Rule rejection.
 *
 * TODO(server-roadmap SRV-CONFIG-001): migrate writes to server-side to
 * eliminate client trust around `recordedFromDeviceId` and to make
 * `recordSnapshot` + `ConfigCurrentRepository.push` atomic (FR-037
 * explicit accepts rare loss until then).
 */
interface ConfigHistoryRepository {

    /**
     * Record a snapshot of the current `/config/current` state.
     * Called on every successful EditorScreen push (FR-036).
     */
    suspend fun recordSnapshot(
        linkId: String,
        snapshot: ConfigSnapshot,
    ): Outcome<Unit, RepositoryError>

    /**
     * Returns snapshots sorted by `recordedAt` DESC (newest first).
     * Backs the history viewer screen (FR-037).
     */
    suspend fun readAll(
        linkId: String,
    ): Outcome<List<ConfigSnapshotWithId>, RepositoryError>

    /**
     * Keep newest [retentionCount] snapshots; delete the rest (FR-038).
     * Idempotent. Run after [recordSnapshot] (best-effort — caller
     * swallows errors to avoid blocking the publish flow).
     *
     * TODO(server-roadmap SRV-CONFIG-002): housekeeping cron on server.
     */
    suspend fun housekeep(
        linkId: String,
        retentionCount: Int = DEFAULT_RETENTION_COUNT,
    ): Outcome<Unit, RepositoryError>

    companion object {
        const val DEFAULT_RETENTION_COUNT: Int = 10
    }
}

data class ConfigSnapshotWithId(
    val autoId: String,
    val snapshot: ConfigSnapshot,
)

sealed interface RepositoryError {
    data class BackendUnavailable(val cause: Throwable?) : RepositoryError
    data class PermissionDenied(val reason: String) : RepositoryError
    data class Corrupt(val cause: Throwable) : RepositoryError
}
