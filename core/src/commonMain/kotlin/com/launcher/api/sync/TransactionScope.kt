package com.launcher.api.sync

import family.wire.WireVersion

import kotlinx.serialization.json.JsonElement

/**
 * Scope captured inside [RemoteSyncBackend.runTransaction]. The block sees a
 * consistent read-set; writes are buffered and applied atomically on success
 * (or retried by the backend on read-set invalidation).
 *
 * Implementations: `FirebaseTransactionScope` (androidMain) wraps Firestore
 * `Transaction`; `FakeRemoteSyncBackend` implements an in-memory equivalent
 * with optimistic-concurrency emulation.
 *
 * Used by `PairingService.claimAsAdmin` to perform the atomic
 * `read /pairings/{token} → assert !claimed && !expired → set claimed=true →
 * create /links/{linkId}` (spec 007 §FR-006).
 */
interface TransactionScope {
    suspend fun get(path: DocPath): DocSnapshot?
    suspend fun set(path: DocPath, data: JsonElement, schemaVersion: WireVersion)
    suspend fun delete(path: DocPath)
}
