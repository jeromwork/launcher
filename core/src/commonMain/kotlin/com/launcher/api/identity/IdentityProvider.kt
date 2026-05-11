package com.launcher.api.identity

import com.launcher.api.result.Outcome

/**
 * Port over the Firebase Auth anonymous-sign-in surface (spec 007 §FR-002).
 *
 *  - `FirebaseIdentityProvider` (androidMain, `realBackend`) — Firebase Auth SDK.
 *  - `FakeIdentityProvider` (commonTest) — deterministic UID factory.
 *
 * TODO(exit-ramp, OWD-2): named auth migration (Google Sign-In / Phone)
 * extends this port with `linkWithCredential(...)` — added when first real
 * user complains about pairing-loss on reinstall (see project-backlog
 * TODO-ARCH-004).
 */
interface IdentityProvider {
    suspend fun signInAnonymous(): Outcome<Identity, IdentityError>
    fun currentIdentity(): Identity?
    suspend fun signOut()
}
