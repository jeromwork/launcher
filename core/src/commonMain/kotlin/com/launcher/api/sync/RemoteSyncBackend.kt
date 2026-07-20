package com.launcher.api.sync

import family.wire.WireVersion

import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Port (spec 007 §FR-010) for remote document read/write/observe over an
 * unspecified backend. **Two implementations** per CLAUDE.md §6 (mock-first):
 *
 *  - `FirebaseRemoteSyncBackend` (androidMain, `realBackend` flavor) — Firestore
 *    SDK adapter; translates every `FirebaseFirestoreException` to
 *    [BackendError] before crossing the module boundary (§FR-013).
 *  - `FakeRemoteSyncBackend` (commonTest) — in-memory state machine with
 *    queue/isStale offline simulation (spec 007 C5).
 *
 * Domain code consumes this port only; no `com.google.firebase.*` import is
 * allowed under `:core/api/` (Konsist fitness function in Phase 10).
 *
 * Backward-compat policy for new operations: add as suspend members with a
 * default exception-throwing impl OR widen the interface in step with both
 * adapters (no single-impl ports — Article XI Anti-Bloat).
 */
interface RemoteSyncBackend {

    /** Idempotent write of a full document body. `schemaVersion` is mirrored
     *  into the payload by the adapter so reads can short-circuit version
     *  routing without re-parsing the body. */
    suspend fun writeDoc(
        path: DocPath,
        data: JsonElement,
        schemaVersion: WireVersion,
    ): Outcome<Unit, BackendError>

    /** `Outcome.Success(null)` means the document does not exist (NOT an error);
     *  [BackendError.NotFound] is reserved for ops that require existence. */
    suspend fun readDoc(path: DocPath): Outcome<DocSnapshot?, BackendError>

    suspend fun deleteDoc(path: DocPath): Outcome<Unit, BackendError>

    /** Hot Flow of snapshot changes. Emits the current snapshot first, then
     *  every subsequent change. `Outcome.Failure(Offline)` may be interleaved
     *  with successful snapshots when connectivity flaps. */
    fun observe(path: DocPath): Flow<Outcome<DocSnapshot?, BackendError>>

    /** Atomic read-modify-write per spec 007 §FR-006 (admin claim transaction).
     *  Block may be re-run by the backend on read-set invalidation; keep it
     *  side-effect-free outside the scope. */
    suspend fun <T> runTransaction(
        block: suspend TransactionScope.() -> T,
    ): Outcome<T, BackendError>

    /** Release listeners, network channels, and any cached resources. Called
     *  on app shutdown or when switching auth identity. */
    suspend fun dispose()
}
