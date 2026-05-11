package com.launcher.api.pairing

import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.link.LinkRegistry
import com.launcher.api.push.PushSender
import com.launcher.api.result.Outcome
import com.launcher.api.sync.RemoteSyncBackend
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/**
 * Pure-domain orchestrator for the pairing FSM (spec 007 §FR-003 .. §FR-009,
 * data-model.md §PairingService). Consumes five ports; **no** Android or
 * Firebase imports — Konsist fitness function in Phase 10 enforces.
 *
 * Naming-neutral by design: the [claimAsAdmin] result type is the **sealed**
 * [TrustEdgeBootstrap]; in spec 007 the single subtype is
 * [com.launcher.api.link.Link]. Future use-cases (contacts spec 011, jitsi
 * trust edges, multi-admin) add their own subtypes **without modifying
 * `PairingService`** — see `TrustEdgeBootstrap` kdoc and memory
 * `project_qr_pairing_trust_primitive.md`.
 *
 * **Signatures-only in Phase 1** — bodies arrive in Phase 7 (T064 .. T072).
 */
class PairingService(
    private val backend: RemoteSyncBackend,
    private val identity: IdentityProvider,
    private val deviceId: DeviceIdProvider,
    private val linkRegistry: LinkRegistry,
    private val pushSender: PushSender,
    private val clock: () -> Long,
    private val random: Random = Random.Default,
) {
    /** Hot Flow of the FSM (see [PairingState]). */
    fun state(): Flow<PairingState> = TODO("Phase 7")

    // ---- Managed-side ----------------------------------------------------

    /** Generate token, write `/pairings/{token}`, return token for QR display. */
    suspend fun startPairingAsManaged(): Outcome<PairingToken, PairingError> = TODO("Phase 7")

    /** Cancel an in-flight pairing on Managed (toggle off before claim). */
    suspend fun cancelPairingAsManaged(): Outcome<Unit, PairingError> = TODO("Phase 7")

    /** Managed user accepted the consent screen. Activates the link and
     *  subscribes to FCM topic `link-{linkId}`. */
    suspend fun confirmConsentAsManaged(): Outcome<com.launcher.api.link.Link, PairingError> = TODO("Phase 7")

    /** Managed user rejected the consent screen — deletes `/pairings/{token}`
     *  fully (FR-008, single-use) and any pre-created `/links/{linkId}`. */
    suspend fun declineConsentAsManaged(): Outcome<Unit, PairingError> = TODO("Phase 7")

    // ---- Admin-side ------------------------------------------------------

    /**
     * Atomic claim transaction (FR-006). Returns the sealed
     * [TrustEdgeBootstrap] — in spec 007 the single subtype is `Link`;
     * see kdoc for the reusable-trust-primitive rationale.
     */
    suspend fun claimAsAdmin(token: PairingToken): Outcome<TrustEdgeBootstrap, PairingError> = TODO("Phase 7")
}
