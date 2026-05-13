package com.launcher.adapters.link

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.launcher.api.identity.AdminIdentity
import com.launcher.api.link.Link
import com.launcher.api.link.ManagedDevicesRegistry
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Admin-side paired-devices view backed by a single Firestore listener on
 * `links.where("adminId", "==", currentUid)`. The listener attaches lazily
 * on first [observeAll] collection and is torn down when no collector is
 * active.
 *
 * **Optimistic local state**: [recordClaim] and [forgetLink] mutate an in-memory
 * [MutableStateFlow] so the UI sees the new card immediately after a successful
 * `claimAsAdmin`, without waiting for the listener round-trip (~200ms on a
 * good connection). When the listener catches up, the snapshot replaces the
 * optimistic state.
 */
class FirestoreManagedDevicesRegistry(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ManagedDevicesRegistry {

    private val local = MutableStateFlow<List<Link>>(emptyList())
    private val debug = MutableStateFlow<List<String>>(emptyList())

    private fun log(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)
            .format(java.util.Date())
        debug.value = (debug.value + "$ts $msg").takeLast(10)
    }

    override fun debugEvents(): Flow<List<String>> = debug

    override fun observeAll(): Flow<List<Link>> = callbackFlow {
        log("observeAll() collect-start, local=${local.value.size}")

        // Always forward `local` updates to collectors — this catches the
        // optimistic recordClaim path and the listener-driven path uniformly.
        val mirrorJob = launch {
            local.collect { trySend(it) }
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            log("observeAll() NO_UID — listener NOT attached")
            awaitClose { mirrorJob.cancel() }
            return@callbackFlow
        }
        log("observeAll() uid=${uid.take(6)} attaching listener")

        val registration: ListenerRegistration = firestore
            .collection("links")
            .whereEqualTo("adminId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    log("listener ERR ${error.message?.take(50)}")
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                log("listener snap docs=${docs.size}")
                val parsed = docs.mapNotNull { d ->
                    val data = d.data ?: return@mapNotNull null
                    val adminUid = data["adminId"] as? String ?: return@mapNotNull null
                    val managedDeviceId = data["managedDeviceId"] as? String ?: return@mapNotNull null
                    val managedFirebaseUid = data["managedDeviceFirebaseUid"] as? String
                        ?: return@mapNotNull null
                    val createdAt = (data["createdAt"] as? Long)
                        ?: (data["createdAt"] as? Number)?.toLong()
                        ?: 0L
                    Link(
                        linkId = d.id,
                        adminId = AdminIdentity(adminUid),
                        managedDeviceId = managedDeviceId,
                        managedDeviceFirebaseUid = managedFirebaseUid,
                        createdAt = createdAt,
                    )
                }
                // Merge listener results with any optimistic entries that
                // haven't yet shown up in the server snapshot.
                val merged = (parsed + local.value).distinctBy { it.linkId }
                local.value = merged
            }
        awaitClose {
            mirrorJob.cancel()
            registration.remove()
        }
    }

    override fun recordClaim(link: Link) {
        if (local.value.any { it.linkId == link.linkId }) {
            log("recordClaim ${link.linkId.take(6)} DUP")
            return
        }
        local.value = local.value + link
        log("recordClaim ${link.linkId.take(6)} OK total=${local.value.size}")
    }

    override fun forgetLink(linkId: String) {
        local.value = local.value.filterNot { it.linkId == linkId }
        log("forgetLink ${linkId.take(6)} total=${local.value.size}")
    }

    override suspend fun findByManagedDeviceId(
        managedDeviceId: String,
    ): Outcome<Link?, BackendError> {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            log("findByMDI NO_UID mdi=${managedDeviceId.take(6)}")
            return Outcome.Success(null)
        }
        log("findByMDI uid=${uid.take(6)} mdi=${managedDeviceId.take(6)}")
        return try {
            val snap = firestore
                .collection("links")
                .whereEqualTo("adminId", uid)
                .whereEqualTo("managedDeviceId", managedDeviceId)
                .limit(1)
                .get()
                .await()
            val doc = snap.documents.firstOrNull() ?: return Outcome.Success(null)
            val data = doc.data ?: return Outcome.Success(null)
            val adminUid = data["adminId"] as? String ?: return Outcome.Success(null)
            val managedFirebaseUid = data["managedDeviceFirebaseUid"] as? String
                ?: return Outcome.Success(null)
            val createdAt = (data["createdAt"] as? Long)
                ?: (data["createdAt"] as? Number)?.toLong()
                ?: 0L
            Outcome.Success(
                Link(
                    linkId = doc.id,
                    adminId = AdminIdentity(adminUid),
                    managedDeviceId = managedDeviceId,
                    managedDeviceFirebaseUid = managedFirebaseUid,
                    createdAt = createdAt,
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Outcome.Failure(BackendError.Unknown(t.message ?: "query failed"))
        }
    }
}
