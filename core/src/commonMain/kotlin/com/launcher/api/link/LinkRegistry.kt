package com.launcher.api.link

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.coroutines.flow.Flow

/**
 * Port (spec 007 §FR-031 .. §FR-033) over the lifecycle of the **current**
 * Managed-side link. The registry holds at most one [Link] at a time —
 * multi-admin (subAdmin) is a future spec.
 *
 *  - `FirestoreLinkRegistry` (androidMain) — backed by [com.launcher.api.sync.RemoteSyncBackend]
 *    plus FCM topic subscribe/unsubscribe on activate/revoke.
 *  - `FakeLinkRegistry` (commonTest) — in-memory.
 *
 *  - [activate]: called after consent.allow (FR-009). Internally writes the
 *    initial `/links/{linkId}/state/current` snapshot and subscribes the
 *    Managed device to FCM topic `link-{linkId}`.
 *  - [revoke]: hard-deletes the entire subtree (FR-033) iterating
 *    [Link.KNOWN_SUBCOLLECTIONS], unsubscribes from the FCM topic, and
 *    flips the in-Settings toggle off.
 */
interface LinkRegistry {
    /** Hot Flow; emits `null` when not paired. */
    fun currentLink(): Flow<Link?>

    suspend fun activate(linkId: String): Outcome<Link, BackendError>

    suspend fun revoke(): Outcome<Unit, BackendError>
}
