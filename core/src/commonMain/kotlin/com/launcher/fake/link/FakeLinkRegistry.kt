package com.launcher.fake.link

import com.launcher.api.link.Link
import com.launcher.api.link.LinkRegistry
import com.launcher.api.link.LinkWireFormat
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.RemoteSyncBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [LinkRegistry] for tests and `mockBackend` flavor (FR-012).
 *
 * Activation modes:
 *  - **Backend-backed** (preferred): pass a [backend] in the constructor —
 *    `activate(linkId)` reads `/links/{linkId}` from the shared
 *    `FakeRemoteSyncBackend` and constructs a [Link] from the wire-format
 *    body. This matches the realBackend's `FirestoreLinkRegistry.activate`
 *    semantics and lets end-to-end pairing tests roundtrip through the
 *    same Firestore-shape doc the admin transaction wrote.
 *  - **Pre-seeded fallback**: when no backend is supplied, callers must
 *    [seedLink] before [activate]; activate then validates the linkId
 *    matches. Used in narrow tests that only exercise the registry, not
 *    the pairing handshake.
 */
class FakeLinkRegistry(
    private val backend: RemoteSyncBackend? = null,
    initial: Link? = null,
) : LinkRegistry {

    private val state = MutableStateFlow(initial)

    override fun currentLink(): Flow<Link?> = state.asStateFlow()

    override suspend fun activate(linkId: String): Outcome<Link, BackendError> {
        backend?.let { b ->
            return when (val read = b.readDoc(DocPath.Links(linkId))) {
                is Outcome.Failure -> Outcome.Failure(read.error)
                is Outcome.Success -> {
                    val snap = read.value ?: return Outcome.Failure(BackendError.NotFound)
                    when (val parsed = LinkWireFormat.deserialize(snap.data)) {
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
                            state.value = link
                            Outcome.Success(link)
                        }
                    }
                }
            }
        }

        // Pre-seeded fallback.
        val current = state.value
        if (current != null && current.linkId == linkId) {
            return Outcome.Success(current)
        }
        return Outcome.Failure(BackendError.NotFound)
    }

    override suspend fun revoke(): Outcome<Unit, BackendError> {
        state.value = null
        return Outcome.Success(Unit)
    }

    // ---- Test hooks ------------------------------------------------------

    fun seedLink(link: Link?) {
        state.value = link
    }

    fun snapshot(): Link? = state.value
}
