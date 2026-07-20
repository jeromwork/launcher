package com.launcher.adapters.sync

import family.wire.WireVersion

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.DocSnapshot
import com.launcher.api.sync.RemoteSyncBackend
import com.launcher.api.sync.TransactionScope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * [RemoteSyncBackend] implementation over Firebase Firestore (FR-011).
 *
 * **Anti-corruption layer** (FR-013, CLAUDE.md §1, §2): every Firestore
 * SDK type is mapped via [FirestoreDocMapper] before crossing the
 * function-signature boundary. The only Firestore types in this file are
 * inside method bodies — never in return types or parameters.
 *
 * **Listener lifecycle**: [observe] returns a cold [Flow]; the SDK
 * [ListenerRegistration] is created on subscribe and removed on cancel
 * (callbackFlow's `awaitClose`). No global listener leak.
 *
 * TODO(spec 013): when offline-detection-with-reactions lands, the
 * [DocSnapshot.isStale] flag here uses `metadata.isFromCache` — extend to
 * surface a richer connectivity signal that ties into the in-app
 * "no internet" banner.
 */
class FirebaseRemoteSyncBackend(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : RemoteSyncBackend {

    override suspend fun writeDoc(
        path: DocPath,
        data: JsonElement,
        schemaVersion: WireVersion,
    ): Outcome<Unit, BackendError> = runCatchingMapped {
        val payload = FirestoreDocMapper.toFirestore(data).toMutableMap()
        payload["schemaVersion"] = schemaVersion
        payload["updatedAt"] = FieldValue.serverTimestamp()
        firestore.document(path.rawPath).set(payload).await()
    }

    override suspend fun readDoc(path: DocPath): Outcome<DocSnapshot?, BackendError> =
        runCatchingMapped {
            val snap = firestore.document(path.rawPath).get().await()
            FirestoreDocMapper.fromFirestore(path, snap)
        }

    override suspend fun deleteDoc(path: DocPath): Outcome<Unit, BackendError> =
        runCatchingMapped {
            firestore.document(path.rawPath).delete().await()
        }

    override fun observe(path: DocPath): Flow<Outcome<DocSnapshot?, BackendError>> = callbackFlow {
        val registration: ListenerRegistration = firestore.document(path.rawPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Outcome.Failure(FirestoreDocMapper.mapException(error)))
                    return@addSnapshotListener
                }
                val mapped = snapshot?.let { FirestoreDocMapper.fromFirestore(path, it) }
                trySend(Outcome.Success(mapped))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun <T> runTransaction(
        block: suspend TransactionScope.() -> T,
    ): Outcome<T, BackendError> = runCatchingMapped {
        suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
            firestore.runTransaction { txn ->
                val scope = FirebaseTransactionScope(firestore, txn)
                // Firestore's transaction body MUST return synchronously, so
                // we bridge the suspend block with runBlocking. This is the
                // single sanctioned use of runBlocking in the project — it's
                // required by the Firestore SDK contract, not a workaround.
                @Suppress("RestrictedApi")
                runBlocking { scope.block() }
            }.addOnSuccessListener { value ->
                if (cont.isActive) cont.resume(value)
            }.addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWith(Result.failure(e))
            }
        }
    }

    override suspend fun dispose() {
        // Firestore SDK manages its own pool; per-instance dispose is a no-op.
        // Listeners are tied to Flow lifecycle (observe()).
    }

    /** Run a suspend block, map any throwable to [BackendError].
     *  [CancellationException] re-thrown so structured-concurrency cancel
     *  propagates correctly. */
    private suspend inline fun <T> runCatchingMapped(
        crossinline block: suspend () -> T,
    ): Outcome<T, BackendError> {
        return try {
            Outcome.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Failure(FirestoreDocMapper.mapException(e))
        }
    }
}
