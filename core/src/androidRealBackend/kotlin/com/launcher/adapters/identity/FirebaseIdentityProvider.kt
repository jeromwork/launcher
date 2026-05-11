package com.launcher.adapters.identity

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.launcher.api.identity.AdminIdentity
import com.launcher.api.identity.Identity
import com.launcher.api.identity.IdentityError
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.identity.ManagedIdentity
import com.launcher.api.result.Outcome
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * [IdentityProvider] backed by Firebase Anonymous Auth (FR-002).
 *
 * **Role discriminator**: this provider is bound twice in the Koin graph —
 * once with [Role.Managed] for the Managed device's scope, once with
 * [Role.Admin] for the admin-mode scope. The Firebase Auth UID is the
 * same shape in both cases; the role tag is a domain-layer concept
 * (data-model.md §AdminIdentity vs §ManagedIdentity).
 *
 * Spec 007 only wires the Managed scope; admin-mode wiring lands in
 * spec 009 (admin commands) — see TODO at the binding site in
 * `BackendInit.kt`.
 *
 * TODO(OWD-2): named auth migration (Google Sign-In / Phone) replaces
 * `signInAnonymously()` with `linkWithCredential(...)` — the port shape
 * doesn't change, only this adapter.
 */
class FirebaseIdentityProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val role: Role,
) : IdentityProvider {

    enum class Role { Admin, Managed }

    override suspend fun signInAnonymous(): Outcome<Identity, IdentityError> {
        val existing = currentIdentity()
        if (existing != null) return Outcome.Success(existing)
        return try {
            val result = auth.signInAnonymously().await()
            val uid = result.user?.uid
                ?: return Outcome.Failure(IdentityError.Unknown("signIn succeeded but user is null"))
            Outcome.Success(buildIdentity(uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            Outcome.Failure(IdentityError.NetworkUnavailable)
        } catch (e: FirebaseTooManyRequestsException) {
            Outcome.Failure(IdentityError.QuotaExceeded)
        } catch (e: Throwable) {
            Outcome.Failure(IdentityError.Unknown(e.message ?: e::class.simpleName ?: "unknown"))
        }
    }

    override fun currentIdentity(): Identity? {
        val uid = auth.currentUser?.uid ?: return null
        return buildIdentity(uid)
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private fun buildIdentity(uid: String): Identity = when (role) {
        Role.Admin -> AdminIdentity(uid)
        Role.Managed -> ManagedIdentity(uid)
    }
}
