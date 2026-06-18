package com.launcher.ui.auth

import com.launcher.api.auth.AuthError
import com.launcher.api.auth.AuthIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignInTriggerStateTest {

    private val identity = AuthIdentity(
        stableId = "test-uuid",
        displayName = "Test User",
        email = "test@example.com",
    )

    @Test
    fun notSignedIn_whenAllInputsNeutral() {
        val state = deriveSignInTriggerState(
            currentUser = null,
            isSigningIn = false,
            isSigningOut = false,
            lastError = null,
        )
        assertEquals(SignInTriggerState.NotSignedIn, state)
    }

    @Test
    fun signingIn_whenIsSigningInTrue() {
        val state = deriveSignInTriggerState(
            currentUser = null,
            isSigningIn = true,
            isSigningOut = false,
            lastError = null,
        )
        assertEquals(SignInTriggerState.SigningIn, state)
    }

    @Test
    fun signedIn_whenCurrentUserPresent() {
        val state = deriveSignInTriggerState(
            currentUser = identity,
            isSigningIn = false,
            isSigningOut = false,
            lastError = null,
        )
        assertEquals(SignInTriggerState.SignedIn(identity), state)
    }

    @Test
    fun signingOut_whenIsSigningOutTrue_overridesEverything() {
        val state = deriveSignInTriggerState(
            currentUser = identity,
            isSigningIn = false,
            isSigningOut = true,
            lastError = null,
        )
        assertEquals(SignInTriggerState.SigningOut, state)
    }

    @Test
    fun error_whenLastErrorPresentAndNotCancelled() {
        val state = deriveSignInTriggerState(
            currentUser = null,
            isSigningIn = false,
            isSigningOut = false,
            lastError = AuthError.NetworkError,
        )
        assertTrue(state is SignInTriggerState.Error)
        assertEquals(AuthError.NetworkError, state.reason)
    }

    @Test
    fun cancelledIsTreatedAsNotSignedIn_notError() {
        // Отмена пользователем — не ошибка для UI; должны вернуть NotSignedIn.
        val state = deriveSignInTriggerState(
            currentUser = null,
            isSigningIn = false,
            isSigningOut = false,
            lastError = AuthError.Cancelled,
        )
        assertEquals(SignInTriggerState.NotSignedIn, state)
    }

    @Test
    fun signingInPrecedesSignedIn_whenBothAreTrue() {
        // Edge case: currentUser != null, но новый signIn() в полёте (например
        // re-link). Показываем SigningIn — пользователь видит progress.
        val state = deriveSignInTriggerState(
            currentUser = identity,
            isSigningIn = true,
            isSigningOut = false,
            lastError = null,
        )
        assertEquals(SignInTriggerState.SigningIn, state)
    }
}
