package com.launcher.api.config

import com.launcher.api.link.PartialReason
import com.launcher.api.sync.BackendError

/**
 * Sealed error hierarchy для spec 008 operations (push, apply, local persistence).
 *
 * Per failure-recovery checklist CHK016/CHK017:
 *  - **Categorical**, not unique error message strings → enables rate measurement.
 *  - Each subtype maps to a specific recovery path в UI (FR-014 merge UI,
 *    FR-015 push spinner, FR-033 partialApplyReasons indicator).
 *
 * Wraps spec 007's [BackendError] when underlying I/O fails (без re-deriving the
 * shape).
 */
sealed interface ConfigSyncError {

    /**
     * Optimistic-concurrency rejection (FR-013): server's `serverUpdatedAt` ≠
     * client's snapshot. The freshly-read [serverConfig] and computed
     * [localDiff] feed Merge UI (FR-014, FR-051).
     */
    data class Conflict(
        val localDiff: ConfigDiff,
        val serverConfig: ConfigDocument,
    ) : ConfigSyncError

    /** Underlying backend (Firestore) failure — network, auth, server error. */
    data class BackendFailure(val cause: BackendError) : ConfigSyncError

    /**
     * Apply partially succeeded — some slots/contacts couldn't be brought live
     * на Managed. Reasons are categorized via [PartialReason].
     * Surfaces in `/state/current.partialApplyReasons` (FR-033).
     */
    data class ApplyPartial(val reasons: List<PartialReason>) : ConfigSyncError

    /**
     * Local SQLDelight DB corruption (rare) — recovery is wipe + fresh start
     * per failure-recovery CHK014.
     */
    data class LocalStorageCorrupt(val cause: Throwable) : ConfigSyncError
}
