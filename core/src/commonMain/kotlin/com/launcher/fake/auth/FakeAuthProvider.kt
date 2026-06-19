package com.launcher.fake.auth

import com.launcher.api.auth.AuthError
import com.launcher.api.auth.AuthIdentity
import com.launcher.api.auth.AuthProvider
import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic [AuthProvider] для `mockBackend` flavor и тестов (FR-004,
 * FR-019, FR-024).
 *
 * Отличается от [com.launcher.api.auth.FakeAuthAdapter] (commonTest) тем,
 * что живёт в `commonMain` — т.е. доступен production-сборкам mockBackend
 * flavor'а. По функциям эквивалентен — тот же API simulate* для test setup.
 *
 * Per spec 017 plan §"Project Structure".
 */
class FakeAuthProvider(
    private val seedIdentity: AuthIdentity = DEFAULT_IDENTITY,
) : AuthProvider {

    private val _currentUser = MutableStateFlow<AuthIdentity?>(null)
    override val currentUser: Flow<AuthIdentity?> = _currentUser.asStateFlow()

    private var pendingError: AuthError? = null

    override suspend fun signIn(): Outcome<AuthIdentity, AuthError> {
        val error = pendingError
        if (error != null) {
            pendingError = null
            return Outcome.Failure(error)
        }
        _currentUser.value = seedIdentity
        return Outcome.Success(seedIdentity)
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    // --- Test simulators ------------------------------------------------------

    fun simulateNetworkError() { pendingError = AuthError.NetworkError }
    fun simulateCancellation() { pendingError = AuthError.Cancelled }
    fun simulateNoEmail() { pendingError = AuthError.NoEmail }
    fun simulateProviderUnavailable() { pendingError = AuthError.ProviderUnavailable }
    fun simulateUnknown(message: String = "fake") { pendingError = AuthError.Unknown(message) }

    fun forceCurrent(identity: AuthIdentity?) { _currentUser.value = identity }

    companion object {
        val DEFAULT_IDENTITY = AuthIdentity(
            stableId = "fake-stable-id-00000000-0000-0000-0000-000000000001",
            displayName = "Fake User",
            email = "fake@example.com",
        )
    }
}
