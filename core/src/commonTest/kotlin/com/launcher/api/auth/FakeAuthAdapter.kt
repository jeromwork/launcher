package com.launcher.api.auth

import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Детерминированная реализация [AuthProvider] для тестов и mockBackend builds.
 *
 * По умолчанию [signIn] возвращает первого пользователя из [fakeUsers] как
 * [Outcome.Success]. Чтобы симулировать ошибку, вызовите соответствующий
 * `simulate*()` метод перед [signIn] — следующий вызов вернёт [Outcome.Failure]
 * с указанной [AuthError], затем симулятор сбрасывается.
 *
 * Per spec 017 FR-024, FR-025, CLAUDE.md §6 (mock-first development).
 */
class FakeAuthAdapter(
    private val fakeUsers: List<AuthIdentity> = listOf(DEFAULT_USER),
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
        val user = fakeUsers.firstOrNull()
            ?: return Outcome.Failure(AuthError.Unknown("FakeAuthAdapter: fakeUsers is empty"))
        _currentUser.value = user
        return Outcome.Success(user)
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

    /** Force `currentUser` to emit the given identity (без signIn flow). */
    fun forceCurrent(identity: AuthIdentity?) { _currentUser.value = identity }

    companion object {
        val DEFAULT_USER = AuthIdentity(
            stableId = "fake-stable-id-00000000-0000-0000-0000-000000000001",
            displayName = "Fake User",
            email = "fake@example.com",
        )
    }
}
