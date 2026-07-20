package com.launcher.adapters.link

import com.google.firebase.firestore.FirebaseFirestore
import com.launcher.adapters.push.FcmRegistration
import com.launcher.adapters.sync.FirestoreDocMapper
import family.pairing.api.EncryptedMediaStorage
import com.launcher.api.link.Link
import com.launcher.api.link.LinkRegistry
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.RemoteSyncBackend
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * [LinkRegistry] implementation backed by Firestore + FCM topic
 * registration (FR-031, FR-033).
 *
 * **State source**: the `currentLink()` Flow is fed by a local
 * [MutableStateFlow] that mirrors the most recently activated/revoked
 * link. Spec 008's bidirectional sync will replace this with a Firestore
 * listener; for now an in-memory mirror is enough because spec 007 only
 * needs the Managed side to know its own currently-paired link.
 *
 * **Revoke** (FR-033) hard-deletes the entire `/links/{linkId}` subtree.
 * Firestore has no built-in recursive delete on the client SDK, so we
 * iterate the [Link.KNOWN_SUBCOLLECTIONS] list. When a new subcollection
 * is added in a future spec, append to that list — search-and-replace is
 * how we keep the revoke contract honest (see TODO at Link.KNOWN_SUBCOLLECTIONS).
 *
 * TODO(server-side recursive delete): when we upgrade to Firebase Blaze
 * (project-backlog TODO-ARCH-003), replace the client-side iteration with
 * a Cloud Function that calls Firestore Admin SDK's recursiveDelete().
 */
@OptIn(ExperimentalUuidApi::class)
class FirestoreLinkRegistry(
    private val backend: RemoteSyncBackend,
    private val firestore: FirebaseFirestore,
    private val fcmRegistration: FcmRegistration,
    // Spec 011 — recursive Storage cleanup на revoke (FR-043). Optional —
    // mockBackend builds или старые tests могут передавать null, тогда
    // Storage cleanup пропускается.
    private val encryptedMediaStorage: EncryptedMediaStorage? = null,
) : LinkRegistry {

    private val state = MutableStateFlow<Link?>(null)

    override fun currentLink(): Flow<Link?> = state.asStateFlow().map { it }

    override suspend fun activate(linkId: String): Outcome<Link, BackendError> {
        return when (val outcome = backend.readDoc(DocPath.Links(linkId))) {
            is Outcome.Failure -> Outcome.Failure(outcome.error)
            is Outcome.Success -> {
                val snap = outcome.value ?: return Outcome.Failure(BackendError.NotFound)
                val parsed = com.launcher.api.link.LinkWireFormat.deserialize(snap.data)
                when (parsed) {
                    is Outcome.Failure -> Outcome.Failure(parsed.error)
                    is Outcome.Success -> {
                        val p = parsed.value
                        val link = Link(
                            linkId = linkId,
                            adminId = p.adminId,
                            managedDeviceId = p.managedDeviceId,
                            managedDeviceFirebaseUid = p.managedDeviceFirebaseUid,
                            createdAt = snap.updatedAt ?: p.createdAt ?: 0L,
                        )
                        fcmRegistration.subscribeToLinkTopic(linkId)
                        state.value = link
                        Outcome.Success(link)
                    }
                }
            }
        }
    }

    override suspend fun revoke(): Outcome<Unit, BackendError> {
        val current = state.value ?: return Outcome.Success(Unit)
        val linkId = current.linkId
        return try {
            // Best-effort: unsubscribe BEFORE delete so we stop receiving
            // push for a link that's about to vanish.
            fcmRegistration.unsubscribeFromLinkTopic(linkId)

            for (sub in Link.KNOWN_SUBCOLLECTIONS) {
                deleteSubcollection(linkId, sub)
            }
            // Spec 011 FR-043 — Storage cleanup: enumerate KNOWN_STORAGE_PATHS,
            // delete все blobs. Сейчас один path — "private-media".
            encryptedMediaStorage?.let { storage ->
                runCatching {
                    val uuids = storage.list(linkId)
                    for (uuid in uuids) {
                        storage.delete(linkId, uuid)
                    }
                }
            }
            backend.deleteDoc(DocPath.Links(linkId))
            state.value = null
            Outcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Failure(FirestoreDocMapper.mapException(e))
        }
    }

    /** Iterates documents under `/links/{linkId}/<sub>` and deletes them in
     *  batches of 500 (Firestore's max batch size). For `state`/`config`/etc.
     *  this is typically 1 document; for `commands` it may be N. */
    private suspend fun deleteSubcollection(linkId: String, sub: String) {
        val collection = firestore.collection("links/$linkId/$sub")
        var pageStart: com.google.firebase.firestore.DocumentSnapshot? = null
        while (true) {
            var query = collection.limit(BATCH_SIZE.toLong())
            if (pageStart != null) query = query.startAfter(pageStart)
            val page = query.get().await()
            if (page.isEmpty) break
            val batch = firestore.batch()
            for (doc in page.documents) batch.delete(doc.reference)
            batch.commit().await()
            if (page.size() < BATCH_SIZE) break
            pageStart = page.documents.last()
        }
    }

    companion object {
        private const val BATCH_SIZE: Int = 500
    }
}
