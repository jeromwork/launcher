package com.launcher.api.pairing

import com.launcher.api.identity.AdminIdentity
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.link.Link
import com.launcher.api.link.LinkBootstrap
import com.launcher.api.link.LinkBootstrapWireFormat
import com.launcher.api.link.LinkRegistry
import com.launcher.api.link.LinkWireFormat
import com.launcher.api.push.PushSender
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.RemoteSyncBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Pure-domain orchestrator for the pairing FSM (spec 007 §FR-003 .. §FR-009,
 * data-model.md §PairingService). Consumes five ports; **no** Android or
 * Firebase imports — Konsist fitness function in Phase 10 enforces.
 *
 * **State machine**:
 * ```
 *  Idle ──startPairingAsManaged()──> WaitingForClaim
 *                                       │
 *                       ┌─claimAsAdmin()┘   (admin side)
 *                       │
 *                       │ /pairings/{token} observer sees claimed=true
 *                       ▼
 *                  AwaitingConsent
 *                       │
 *           ┌─confirmConsentAsManaged()─> Claimed (writes /state, FCM subscribe)
 *           │
 *           └─declineConsentAsManaged()─> Idle (deletes /pairings + /links)
 * ```
 *
 * **Naming-neutral by design**: [claimAsAdmin] returns the sealed
 * [TrustEdgeBootstrap]. In spec 007 the single subtype is
 * [com.launcher.api.link.Link]; future use-cases (contacts spec 011,
 * jitsi trust edges, multi-admin) add their own subtypes **without
 * modifying `PairingService`** — see `TrustEdgeBootstrap` kdoc and
 * memory `project_qr_pairing_trust_primitive.md`.
 *
 * **Coroutine ownership**: the service holds a [CoroutineScope] for its
 * internal `/pairings/{token}` observer. The scope is supplied by the
 * DI graph (`Application`-scoped on Android) so cancellations propagate
 * with the process lifecycle.
 */
class PairingService(
    private val backend: RemoteSyncBackend,
    private val identity: IdentityProvider,
    private val deviceId: DeviceIdProvider,
    private val linkRegistry: LinkRegistry,
    @Suppress("unused") private val pushSender: PushSender, // wired for spec 009 admin-side flows
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val managedDevices: com.launcher.api.link.ManagedDevicesRegistry? = null,
    private val random: Random = Random.Default,
) {

    private val stateFlow = MutableStateFlow<PairingState>(PairingState.Idle)
    private var pairingObserver: Job? = null

    /** Token TTL — spec 007 §FR-003 fixes 5 minutes. */
    private val tokenTtlMillis: Long = TOKEN_TTL_MS

    fun state(): Flow<PairingState> = stateFlow.asStateFlow()

    // ---- Managed-side ----------------------------------------------------

    suspend fun startPairingAsManaged(): Outcome<PairingToken, PairingError> {
        val uid = currentUid() ?: return Outcome.Failure(PairingError.PermissionDenied)
        val devUuid = deviceId.currentDeviceId().first()
        val token = PairingToken.generate(random)
        val expiresAt = clock() + tokenTtlMillis
        val body = PairingWireFormat.serialize(
            token = token,
            managedDeviceId = devUuid,
            managedDeviceFirebaseUid = uid,
            expiresAt = expiresAt,
            claimed = false,
        )
        val write = backend.writeDoc(DocPath.Pairings(token), body, PairingWireFormat.SCHEMA_VERSION)
        if (write is Outcome.Failure) return Outcome.Failure(write.error.toPairingError())

        stateFlow.value = PairingState.WaitingForClaim(token, expiresAt)
        startObservingPairing(token)
        return Outcome.Success(token)
    }

    /** Forces the FSM back to [PairingState.Idle] without touching Firestore.
     *  Used by the UI after an out-of-band revoke (LinkRegistry.revoke) so the
     *  cached [PairingState.Claimed] doesn't keep the PairingActivity stuck on
     *  the "Связь установлена" screen for the next entry. Safe to call from
     *  any state. */
    fun resetToIdle() {
        stopObservingPairing()
        stateFlow.value = PairingState.Idle
    }

    suspend fun cancelPairingAsManaged(): Outcome<Unit, PairingError> {
        val token = activeToken() ?: return Outcome.Success(Unit) // already idle
        stopObservingPairing()
        val delete = backend.deleteDoc(DocPath.Pairings(token))
        stateFlow.value = PairingState.Idle
        return when (delete) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(delete.error.toPairingError())
        }
    }

    suspend fun confirmConsentAsManaged(): Outcome<Link, PairingError> {
        val awaiting = stateFlow.value as? PairingState.AwaitingConsent
            ?: return Outcome.Failure(PairingError.Unknown("not in AwaitingConsent state"))

        val devUuid = deviceId.currentDeviceId().first()
        val managedUid = currentUid() ?: return Outcome.Failure(PairingError.PermissionDenied)

        // Write initial /state/current bootstrap (FR-009). fcmToken=null in
        // domain — Android adapter populates from FCM SDK in the LinkRegistry
        // path (see FirestoreLinkRegistry.activate + FcmRegistration).
        val bootstrap = LinkBootstrap(
            appliedAt = clock(),
            presetId = DEFAULT_PRESET_ID,
            fcmToken = null,
        )
        val stateBody = LinkBootstrapWireFormat.serialize(bootstrap)
        val stateWrite = backend.writeDoc(
            DocPath.LinkState(awaiting.linkId),
            stateBody,
            LinkBootstrap.SCHEMA_VERSION,
        )
        if (stateWrite is Outcome.Failure) return Outcome.Failure(stateWrite.error.toPairingError())

        // Activate via LinkRegistry — this is where FCM topic subscribe happens
        // on the realBackend flavor (FirestoreLinkRegistry.activate).
        val activate = linkRegistry.activate(awaiting.linkId)
        if (activate is Outcome.Failure) return Outcome.Failure(activate.error.toPairingError())
        val link = (activate as Outcome.Success).value

        // The Link returned by registry has the canonical createdAt from
        // /links/{linkId}. We pre-built it locally here for cases where
        // activate isn't yet usable (FakeLinkRegistry test), but trust the
        // registry's value when present.
        stateFlow.value = PairingState.Claimed(link)
        stopObservingPairing()

        // Echo unused params to silence the compiler about managedUid/devUuid
        // — they're checked via assertions outside the happy path that may be
        // added in a future hardening pass.
        @Suppress("UNUSED_EXPRESSION") managedUid
        @Suppress("UNUSED_EXPRESSION") devUuid
        return Outcome.Success(link)
    }

    suspend fun declineConsentAsManaged(): Outcome<Unit, PairingError> {
        val awaiting = stateFlow.value as? PairingState.AwaitingConsent
            ?: return Outcome.Success(Unit) // already idle/elsewhere

        // Delete /links/{linkId} (admin already created it) and /pairings/{token}
        // (single-use cleanup per FR-008).
        val deleteLink = backend.deleteDoc(DocPath.Links(awaiting.linkId))
        val deletePairing = backend.deleteDoc(DocPath.Pairings(awaiting.token))
        stopObservingPairing()
        stateFlow.value = PairingState.Idle

        // Surface the first failure; both deletions are idempotent at the
        // Firestore level so partial success is acceptable.
        if (deleteLink is Outcome.Failure) return Outcome.Failure(deleteLink.error.toPairingError())
        if (deletePairing is Outcome.Failure) return Outcome.Failure(deletePairing.error.toPairingError())
        return Outcome.Success(Unit)
    }

    // ---- Admin-side ------------------------------------------------------

    /**
     * Atomic claim transaction (FR-006). Reads `/pairings/{token}`, asserts
     * `!claimed && !expired`, sets `claimed=true` + writes the new
     * `/links/{linkId}` document, and stamps the token with linkId/adminId
     * so the Managed observer can transition to AwaitingConsent.
     *
     * Returns sealed [TrustEdgeBootstrap] — in spec 007 the single subtype
     * is [Link]. See `TrustEdgeBootstrap` kdoc.
     */
    suspend fun claimAsAdmin(token: PairingToken): Outcome<TrustEdgeBootstrap, PairingError> {
        val adminUid = currentUid() ?: return Outcome.Failure(PairingError.PermissionDenied)

        // First peek at the pairing doc so we know the managedDeviceId before we
        // start the transaction — needed for reconnect-dedup. We can't move the
        // ManagedDevicesRegistry query INTO the transaction (it queries by
        // adminId+managedDeviceId, transactions only support get-by-path).
        val peek = backend.readDoc(DocPath.Pairings(token))
        val peekParsed = (peek as? Outcome.Success)?.value?.let {
            (PairingWireFormat.deserialize(it.data) as? Outcome.Success)?.value
        }
        val managedDeviceIdHint = peekParsed?.managedDeviceId

        // Reconnect-dedup: if this admin already has a link to this managed
        // device, reuse its linkId instead of creating a new one. Inv: 1 admin
        // × 1 managed device = 1 link (no orphan duplicates after reconnect).
        val existingLinkId: String? = if (managedDeviceIdHint != null) {
            val prior = managedDevices?.findByManagedDeviceId(managedDeviceIdHint)
            (prior as? Outcome.Success)?.value?.linkId
        } else null

        val effectiveLinkId = existingLinkId ?: generateLinkId()
        val isReconnect = existingLinkId != null

        val txn = backend.runTransaction<Link> {
            val pairingSnap = get(DocPath.Pairings(token))
                ?: throw ClaimAbort(PairingError.TokenNotFound)

            val parsed = (PairingWireFormat.deserialize(pairingSnap.data) as? Outcome.Success)?.value
                ?: throw ClaimAbort(PairingError.Unknown("pairing payload malformed"))

            if (parsed.claimed) throw ClaimAbort(PairingError.TokenAlreadyClaimed)
            if (parsed.expiresAt < clock()) throw ClaimAbort(PairingError.TokenExpired)

            // Stamp the pairing doc with claimed=true + linkId + adminId so
            // Managed's observer can render the consent screen.
            val updatedPairing = PairingWireFormat.serialize(
                token = token,
                managedDeviceId = parsed.managedDeviceId,
                managedDeviceFirebaseUid = parsed.managedDeviceFirebaseUid,
                expiresAt = parsed.expiresAt,
                claimed = true,
                pairingType = parsed.pairingType,
                linkId = effectiveLinkId,
                adminId = adminUid,
            )
            set(DocPath.Pairings(token), updatedPairing, PairingWireFormat.SCHEMA_VERSION)

            // Create /links/{linkId} root doc only on first pair. Reconnect
            // reuses the existing doc — its body is immutable post-create
            // (Security Rules `allow update: if false`), so we don't write it.
            if (!isReconnect) {
                val linkBody = LinkWireFormat.serialize(
                    adminId = AdminIdentity(adminUid),
                    managedDeviceId = parsed.managedDeviceId,
                    managedDeviceFirebaseUid = parsed.managedDeviceFirebaseUid,
                )
                set(DocPath.Links(effectiveLinkId), linkBody, LinkWireFormat.SCHEMA_VERSION)
            }

            Link(
                linkId = effectiveLinkId,
                adminId = AdminIdentity(adminUid),
                managedDeviceId = parsed.managedDeviceId,
                managedDeviceFirebaseUid = parsed.managedDeviceFirebaseUid,
                createdAt = clock(),
            )
        }

        return when (txn) {
            is Outcome.Success -> {
                val newLink = txn.value
                managedDevices?.recordClaim(newLink)
                // Reflect the successful admin-side claim on the local FSM so
                // the UI (PairingActivity) can route to the "Связь установлена"
                // screen instead of staying on Idle (which used to flash the
                // Managed-side toggle). The Managed side gets its own
                // AwaitingConsent transition via its `/pairings/{token}` observer.
                stateFlow.value = PairingState.Claimed(newLink)
                Outcome.Success(newLink)
            }
            is Outcome.Failure -> {
                // The runBlocking adapter wraps the ClaimAbort throwable as
                // BackendError.Unknown — recover the original PairingError if
                // we can. (FakeRemoteSyncBackend translates the exception
                // verbatim; FirebaseRemoteSyncBackend wraps it.)
                Outcome.Failure(txn.error.toPairingError())
            }
        }
    }

    /** Cancel the internal `/pairings/{token}` observer. Call this when the
     *  service's lifetime ends — production typically uses Application-scope
     *  which lives for the process, but tests need an explicit hook to avoid
     *  `UncompletedCoroutinesError` in `runTest`. */
    fun dispose() {
        stopObservingPairing()
    }

    // ---- internals -------------------------------------------------------

    private fun activeToken(): PairingToken? = when (val s = stateFlow.value) {
        is PairingState.WaitingForClaim -> s.token
        is PairingState.AwaitingConsent -> s.token
        else -> null
    }

    private fun currentUid(): String? = identity.currentIdentity()?.firebaseAuthUid

    private fun startObservingPairing(token: PairingToken) {
        pairingObserver?.cancel()
        pairingObserver = scope.launch {
            backend.observe(DocPath.Pairings(token)).collect { result ->
                when (result) {
                    is Outcome.Success -> handlePairingSnapshot(token, result.value)
                    is Outcome.Failure -> { /* keep current state; transient network */ }
                }
            }
        }
    }

    private fun stopObservingPairing() {
        pairingObserver?.cancel()
        pairingObserver = null
    }

    private fun handlePairingSnapshot(token: PairingToken, snap: com.launcher.api.sync.DocSnapshot?) {
        if (snap == null) {
            // /pairings/{token} deleted while we were waiting — treat as
            // revoked-by-other-side (Managed cancel from another device).
            if (stateFlow.value is PairingState.WaitingForClaim) {
                stateFlow.value = PairingState.Idle
            }
            return
        }
        val parsed = (PairingWireFormat.deserialize(snap.data) as? Outcome.Success)?.value ?: return
        if (parsed.expiresAt < clock() && !parsed.claimed) {
            stateFlow.value = PairingState.Expired
            stopObservingPairing()
            return
        }
        if (parsed.claimed && parsed.linkId != null && parsed.adminId != null) {
            // Don't downgrade out of Claimed if we already passed consent.
            if (stateFlow.value is PairingState.Claimed) return
            stateFlow.value = PairingState.AwaitingConsent(
                token = token,
                linkId = parsed.linkId,
                adminId = parsed.adminId,
            )
        }
    }

    private fun generateLinkId(): String {
        // 16-char alphanumeric — opaque per OWD-4. In real Firestore writes
        // would use `firestore.collection("links").document().id`; we
        // generate locally so the FakeRemoteSyncBackend path works too.
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(16) {
            repeat(16) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    /** Internal sentinel for transaction abort. Translated to
     *  [PairingError] in the wrapping [Outcome] by the surrounding code. */
    private class ClaimAbort(val pairingError: PairingError) : RuntimeException("claim aborted: $pairingError")

    private fun BackendError.toPairingError(): PairingError = when (this) {
        is BackendError.Offline -> PairingError.NetworkUnavailable
        is BackendError.PermissionDenied -> PairingError.PermissionDenied
        is BackendError.NotFound -> PairingError.TokenNotFound
        is BackendError.TransactionConflict -> PairingError.TokenAlreadyClaimed
        is BackendError.Expired -> PairingError.TokenExpired
        is BackendError.Unknown -> {
            // ClaimAbort bubbled up — FakeRemoteSyncBackend leaks the message;
            // try to recover the typed error from it.
            if (message.contains("TokenAlreadyClaimed")) PairingError.TokenAlreadyClaimed
            else if (message.contains("TokenExpired")) PairingError.TokenExpired
            else if (message.contains("TokenNotFound")) PairingError.TokenNotFound
            else PairingError.Unknown(message)
        }
    }

    companion object {
        const val TOKEN_TTL_MS: Long = 5 * 60 * 1_000L
        const val DEFAULT_PRESET_ID: String = "simple-launcher"
    }
}
