package family.keys.fakes

import family.keys.api.AuthIdentity
import family.keys.api.IdentityError
import family.keys.api.IdentityProof
import family.keys.api.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Detrministic fake [IdentityProof] для тестов (CLAUDE.md rule 6 mock-first).
 *
 * Конфигурируется через конструктор:
 *  • [initialIdentity] = null → не signed in изначально.
 *  • [signInResult] = что вернёт [requestSignIn] — fixed identity или error.
 *
 * Внешний код может вручную сменить identity через [setIdentity] (симуляция
 * Sign-Out / Sign-In другого пользователя в multi-identity тестах).
 */
class FakeIdentityProof(
    initialIdentity: AuthIdentity? = null,
    private val signInResult: Outcome<AuthIdentity, IdentityError> = Outcome.Failure(IdentityError.Cancelled)
) : IdentityProof {

    private val state = MutableStateFlow(initialIdentity)

    override suspend fun currentIdentity(): AuthIdentity? = state.value

    override val identityFlow: StateFlow<AuthIdentity?> = state

    override suspend fun requestSignIn(): Outcome<AuthIdentity, IdentityError> {
        if (signInResult is Outcome.Success) {
            state.value = signInResult.value
        }
        return signInResult
    }

    override suspend fun signOut(): Outcome<Unit, IdentityError> {
        state.value = null
        return Outcome.Success(Unit)
    }

    /** Test hook: вручную сменить identity. */
    fun setIdentity(identity: AuthIdentity?) {
        state.value = identity
    }
}
