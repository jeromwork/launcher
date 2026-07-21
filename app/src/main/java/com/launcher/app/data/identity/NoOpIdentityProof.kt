package com.launcher.app.data.identity

import family.keys.api.AuthIdentity
import family.keys.api.IdentityError
import family.keys.api.IdentityProof
import family.keys.api.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [IdentityProof] для non-GMS устройств (T048, FR-028).
 *
 * Возвращает [IdentityError.NoSupportedProvider] на любую попытку Sign-In.
 * `currentIdentity()` всегда null. UI должен показать «облачные функции
 * недоступны на этом устройстве».
 *
 * Используется через [com.launcher.api.auth.AuthAdapterSelector] flavor logic.
 */
class NoOpIdentityProof : IdentityProof {
    override suspend fun currentIdentity(): AuthIdentity? = null
    override val identityFlow: Flow<AuthIdentity?> = flowOf(null)
    override suspend fun requestSignIn(): Outcome<AuthIdentity, IdentityError> =
        Outcome.Failure(IdentityError.NoSupportedProvider)
    override suspend fun signOut(): Outcome<Unit, IdentityError> = Outcome.Success(Unit)
}
