package com.launcher.api.paired

import kotlinx.coroutines.flow.Flow

/**
 * Spec 010 T081 — local-first revocation flag store (FR-032).
 *
 * Captures "the user has tapped «Прекратить помощь»" *immediately* and
 * *persistently* (survives app kill, restart, offline). The companion
 * server-side cleanup is queued via [com.launcher.adapters.paired.UnlinkCleanupWorker]
 * (FR-032a) — this store is the source-of-truth for "локально уже отвязано".
 *
 * Contract:
 *  - [markRevoked]: idempotent set-insert; second call for same linkId is a
 *    no-op (won't re-enqueue a duplicate cleanup worker if
 *    [ExistingWorkPolicy.KEEP] is used downstream).
 *  - [isRevoked]: hot [Flow] — emits the current flag on collection AND
 *    re-emits when the flag flips (Compose `collectAsState`-friendly).
 *  - [clearRevoked]: invoked by the cleanup worker on success path
 *    (FR-032a path (a)/(c)); idempotent for missing entries.
 *  - [revokedLinkIds]: hot [Flow] of the full set — used by the worker
 *    enqueue logic on app cold-start to re-issue any pending cleanup
 *    attempts (FR-032a retry path).
 *
 * Implementations:
 *  - [com.launcher.fake.paired.InMemoryLocalLinkRevocationStore] — commonMain
 *    fake for tests and the `mockBackend` flavor (CLAUDE.md rule 6).
 *  - `DataStoreLocalLinkRevocationStore` (androidMain) — Preferences DataStore
 *    backed; persists across process death.
 *
 * Why a flag-set (not a queue): WorkManager already owns the "what to retry"
 * queue. This store answers a single question — *"did the user revoke this
 * link locally?"* — that any consumer (ConfigEditor, state-publisher, UI)
 * can subscribe to without depending on WorkManager.
 */
interface LocalLinkRevocationStore {

    suspend fun markRevoked(linkId: String)

    fun isRevoked(linkId: String): Flow<Boolean>

    suspend fun clearRevoked(linkId: String)

    fun revokedLinkIds(): Flow<Set<String>>
}
