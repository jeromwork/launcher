package com.launcher.app.data.identity

import com.launcher.api.auth.AuthError
import com.launcher.api.auth.AuthIdentity as F4AuthIdentity
import com.launcher.api.auth.AuthProvider
import com.launcher.api.result.Outcome as F4Outcome
import cryptokit.keys.api.AuthIdentity as F5AuthIdentity
import cryptokit.keys.api.IdentityError
import cryptokit.keys.api.IdentityProof
import cryptokit.keys.api.Outcome as F5Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * F-5 [IdentityProof] adapter поверх F-4 [AuthProvider] (T047, FR-007).
 *
 * Единственное место в codebase где F-4 AuthProvider используется снаружи F-4 модуля
 * (per CLAUDE.md rule 2 ACL): F-5 не ссылается на типы из F-4 напрямую, только через
 * этот adapter.
 *
 * **Mapping**:
 *  • F4 `AuthIdentity` (data class с stableId/displayName/email) ↔ F5 `AuthIdentity`
 *    (точно та же shape) — straightforward конверсия.
 *  • F4 `AuthError.Cancelled` → F5 `IdentityError.Cancelled`.
 *  • F4 `AuthError.ProviderUnavailable` → F5 `IdentityError.NoSupportedProvider`.
 *  • Прочие F4 errors → F5 `IdentityError.Failure(cause)`.
 */
class GoogleSignInIdentityProof(
    private val authProvider: AuthProvider
) : IdentityProof {

    override suspend fun currentIdentity(): F5AuthIdentity? =
        authProvider.currentUser.first()?.toF5()

    override val identityFlow: Flow<F5AuthIdentity?> =
        authProvider.currentUser.map { it?.toF5() }

    override suspend fun requestSignIn(): F5Outcome<F5AuthIdentity, IdentityError> {
        return when (val r = authProvider.signIn()) {
            is F4Outcome.Success -> F5Outcome.Success(r.value.toF5())
            is F4Outcome.Failure -> F5Outcome.Failure(r.error.toF5())
        }
    }

    override suspend fun signOut(): F5Outcome<Unit, IdentityError> {
        return try {
            authProvider.signOut()
            F5Outcome.Success(Unit)
        } catch (t: Throwable) {
            F5Outcome.Failure(IdentityError.Failure(t))
        }
    }

    private fun F4AuthIdentity.toF5(): F5AuthIdentity =
        F5AuthIdentity(stableId = stableId, displayName = displayName, email = email)

    private fun AuthError.toF5(): IdentityError = when (this) {
        is AuthError.Cancelled -> IdentityError.Cancelled
        is AuthError.ProviderUnavailable -> IdentityError.NoSupportedProvider
        else -> IdentityError.Failure(RuntimeException("F-4 AuthError: $this"))
    }
}
