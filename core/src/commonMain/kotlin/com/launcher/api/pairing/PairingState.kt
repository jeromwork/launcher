package com.launcher.api.pairing

import com.launcher.api.link.Link

/**
 * Hot state of the pairing FSM (spec 007 §FR-003 .. §FR-009). Surfaced by
 * [PairingService.state] as a [kotlinx.coroutines.flow.Flow] so the UI can
 * render the QR / consent / paired screens without polling.
 *
 *  - [Idle]: toggle off, no token in flight.
 *  - [WaitingForClaim]: Managed has written `/pairings/{token}`; QR is shown
 *    with the countdown until [expiresAt] (epoch millis, see DocSnapshot TODO).
 *  - [AwaitingConsent]: admin transaction succeeded — `/pairings/{token}`
 *    now carries `claimed=true, linkId, adminId`. Managed shows the
 *    consent screen with the supplied [adminId] and the fixed category
 *    list (FR-007). User taps Allow → [Claimed]; Decline → [Idle] with
 *    full cleanup (FR-008).
 *  - [Claimed]: consent granted; `/links/{linkId}/state/current` written
 *    and Managed subscribed to FCM topic `link-{linkId}` (FR-009).
 *  - [Expired]: token TTL elapsed before claim — UI offers "regenerate".
 *  - [Revoked]: link was hard-deleted by the Managed side (FR-033).
 *  - [Error]: terminal-for-this-attempt; UI surfaces user-friendly text
 *    (see project-backlog TODO-UX-001).
 */
sealed interface PairingState {
    data object Idle : PairingState

    data class WaitingForClaim(
        val token: PairingToken,
        val expiresAt: Long,
    ) : PairingState

    data class AwaitingConsent(
        val token: PairingToken,
        val linkId: String,
        val adminId: String,
    ) : PairingState

    data class Claimed(val link: Link) : PairingState

    data object Expired : PairingState

    data object Revoked : PairingState

    data class Error(val cause: PairingError) : PairingState
}
