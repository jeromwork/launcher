package com.launcher.fake.sync

import com.launcher.wire.WireVersion

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.DocSnapshot
import com.launcher.api.sync.RemoteSyncBackend
import com.launcher.api.sync.TransactionScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.cancellation.CancellationException

/**
 * In-memory [RemoteSyncBackend] for tests AND the `mockBackend` build flavor
 * (FR-012, FR-034). Implements the **offline queue with isStale** semantics
 * required by spec 007 C5 so domain code can be exercised against connectivity
 * flaps without a real Firestore emulator.
 *
 * **Concurrency model**: this Fake is single-coroutine-safe. Tests typically
 * run on a single dispatcher; we do NOT add locks because adding multi-thread
 * safety would force a different shape from the Firestore SDK (which is
 * already multi-thread-safe internally). For multi-process scenarios use the
 * Firebase Emulator integration tier (Phase 9).
 *
 * **Offline behaviour** ([setOnline]`(false)`):
 *  - [writeDoc] / [deleteDoc] go into an ordered queue; in-memory snapshot is
 *    NOT touched, observers are NOT fired.
 *  - [readDoc] returns the **last-known snapshot** with `isStale = true`.
 *  - [runTransaction] fails immediately with [BackendError.Offline] (Firestore
 *    transactions require network).
 *
 * **Online recovery** ([setOnline]`(true)`):
 *  - Queue flushes in FIFO order; each entry fires observers with a `false`
 *    `isStale`.
 *
 * **TODO(spec 013)**: when offline-detection-with-reactions ships, hook into
 * connectivity changes here so dev-mode `mockBackend` can simulate
 * intermittent connectivity without test code calling [setOnline].
 */
class FakeRemoteSyncBackend : RemoteSyncBackend {

    private data class Stored(val snapshot: DocSnapshot)

    private val store: MutableMap<String, Stored> = mutableMapOf()

    /** Per-path SharedFlow for observers. `MutableSharedFlow(replay=1)` so
     *  observers see the latest value on subscribe (matches Firestore semantics). */
    private val observers: MutableMap<String, MutableSharedFlow<Outcome<DocSnapshot?, BackendError>>> = mutableMapOf()

    private val online = MutableStateFlow(true)

    private sealed interface QueuedOp {
        val path: DocPath
        data class Write(override val path: DocPath, val data: JsonElement, val schemaVersion: WireVersion) : QueuedOp
        data class Delete(override val path: DocPath) : QueuedOp
    }

    private val offlineQueue: MutableList<QueuedOp> = mutableListOf()

    /** Per [DocPath.rawPath] monotonic server-style timestamp simulator. Starts
     *  at 1 and increments on each commit so tests can rely on ordering without
     *  depending on a wall clock. */
    private var fakeClock: Long = 1L

    // ---- Test hooks ------------------------------------------------------

    /** Flip the simulated network state. `false` queues writes; `true` flushes
     *  the queue in FIFO order. */
    suspend fun setOnline(value: Boolean) {
        val wasOnline = online.value
        online.value = value
        if (!wasOnline && value) {
            flushQueueLocked()
        }
    }

    /** Direct snapshot peek for tests. Returns the stored snapshot without the
     *  `isStale` flag — use the public [readDoc] for the user-facing path. */
    fun peek(path: DocPath): DocSnapshot? = store[path.rawPath]?.snapshot

    /** Number of writes currently queued because of [setOnline]`(false)`. */
    fun queuedOperationCount(): Int = offlineQueue.size

    // ---- RemoteSyncBackend -----------------------------------------------

    override suspend fun writeDoc(
        path: DocPath,
        data: JsonElement,
        schemaVersion: WireVersion,
    ): Outcome<Unit, BackendError> {
        if (!online.value) {
            offlineQueue.add(QueuedOp.Write(path, data, schemaVersion))
            return Outcome.Success(Unit)
        }
        commitWrite(path, data, schemaVersion)
        return Outcome.Success(Unit)
    }

    override suspend fun readDoc(path: DocPath): Outcome<DocSnapshot?, BackendError> {
        val stored = store[path.rawPath]?.snapshot
        if (!online.value) {
            return Outcome.Success(stored?.copy(isStale = true))
        }
        return Outcome.Success(stored)
    }

    override suspend fun deleteDoc(path: DocPath): Outcome<Unit, BackendError> {
        if (!online.value) {
            offlineQueue.add(QueuedOp.Delete(path))
            return Outcome.Success(Unit)
        }
        commitDelete(path)
        return Outcome.Success(Unit)
    }

    override fun observe(path: DocPath): Flow<Outcome<DocSnapshot?, BackendError>> {
        val flow = observers.getOrPut(path.rawPath) { MutableSharedFlow(replay = 1, extraBufferCapacity = 16) }
        val initialValue: Outcome<DocSnapshot?, BackendError> = Outcome.Success(
            store[path.rawPath]?.snapshot?.let { if (!online.value) it.copy(isStale = true) else it }
        )
        // Seed the replay cache so first collector sees current state without
        // having to wait for the next write.
        flow.tryEmit(initialValue)
        return flow.asSharedFlow()
    }

    override suspend fun <T> runTransaction(
        block: suspend TransactionScope.() -> T,
    ): Outcome<T, BackendError> {
        if (!online.value) return Outcome.Failure(BackendError.Offline)

        val scope = FakeTransactionScope()
        val result = try {
            scope.block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransactionAbort) {
            return Outcome.Failure(e.error)
        } catch (e: Throwable) {
            // Domain code should never throw out of a transaction block in
            // normal flow; we surface as Unknown rather than mask.
            return Outcome.Failure(BackendError.Unknown(
                "transaction block threw: ${e::class.simpleName}: ${e.message}"
            ))
        }
        // Commit phase — all-or-nothing.
        for (op in scope.pendingOps) {
            when (op) {
                is QueuedOp.Write -> commitWrite(op.path, op.data, op.schemaVersion)
                is QueuedOp.Delete -> commitDelete(op.path)
            }
        }
        return Outcome.Success(result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun dispose() {
        store.clear()
        observers.values.forEach { it.resetReplayCache() }
        observers.clear()
        offlineQueue.clear()
    }

    // ---- Internals -------------------------------------------------------

    private fun commitWrite(path: DocPath, data: JsonElement, schemaVersion: WireVersion) {
        val snapshot = DocSnapshot(
            path = path,
            data = data,
            schemaVersion = schemaVersion,
            updatedAt = nextClock(),
            isStale = false,
        )
        store[path.rawPath] = Stored(snapshot)
        observers[path.rawPath]?.tryEmit(Outcome.Success(snapshot))
    }

    private fun commitDelete(path: DocPath) {
        store.remove(path.rawPath)
        observers[path.rawPath]?.tryEmit(Outcome.Success(null))
    }

    private fun flushQueueLocked() {
        val ops = offlineQueue.toList()
        offlineQueue.clear()
        for (op in ops) {
            when (op) {
                is QueuedOp.Write -> commitWrite(op.path, op.data, op.schemaVersion)
                is QueuedOp.Delete -> commitDelete(op.path)
            }
        }
    }

    private fun nextClock(): Long = fakeClock++

    /** Thrown by transaction blocks to abort with a typed error (e.g. claim
     *  preconditions failed). Not a public API — callers return
     *  [Outcome.Failure] from [runTransaction] result. */
    private class TransactionAbort(val error: BackendError) : RuntimeException()

    private inner class FakeTransactionScope : TransactionScope {
        val pendingOps: MutableList<QueuedOp> = mutableListOf()

        override suspend fun get(path: DocPath): DocSnapshot? {
            // First check pending writes inside this transaction so the block
            // sees its own writes (read-your-own-writes inside transaction).
            for (op in pendingOps.asReversed()) {
                if (op.path.rawPath == path.rawPath) {
                    return when (op) {
                        is QueuedOp.Write -> DocSnapshot(path, op.data, op.schemaVersion, updatedAt = null)
                        is QueuedOp.Delete -> null
                    }
                }
            }
            return store[path.rawPath]?.snapshot
        }

        override suspend fun set(path: DocPath, data: JsonElement, schemaVersion: WireVersion) {
            pendingOps.add(QueuedOp.Write(path, data, schemaVersion))
        }

        override suspend fun delete(path: DocPath) {
            pendingOps.add(QueuedOp.Delete(path))
        }
    }
}
