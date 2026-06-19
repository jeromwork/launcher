package com.launcher.ui.auth

import com.launcher.api.auth.AuthError
import com.launcher.api.auth.AuthIdentity

/**
 * 5 explicit UI-состояний для [SignInTrigger]. Per research.md §R7:
 *  - NotSignedIn: дефолт, currentUser == null.
 *  - SigningIn: пользователь нажал кнопку, signIn() в полёте.
 *  - SignedIn: success, есть identity.
 *  - SigningOut: пользователь нажал «Выйти», signOut() в полёте.
 *  - Error: предыдущая попытка signIn провалилась (не Cancelled).
 *
 * Per spec 017 FR-033, data-model.md §"SignInTriggerState".
 */
sealed class SignInTriggerState {
    object NotSignedIn : SignInTriggerState()
    object SigningIn : SignInTriggerState()
    data class SignedIn(val identity: AuthIdentity) : SignInTriggerState()
    object SigningOut : SignInTriggerState()
    data class Error(val reason: AuthError) : SignInTriggerState()
}

/**
 * Pure-функция, выводящая [SignInTriggerState] из 4 атомарных сигналов.
 * Вынесена как top-level для unit-тестируемости (без Compose runtime).
 */
fun deriveSignInTriggerState(
    currentUser: AuthIdentity?,
    isSigningIn: Boolean,
    isSigningOut: Boolean,
    lastError: AuthError?,
): SignInTriggerState = when {
    isSigningOut -> SignInTriggerState.SigningOut
    isSigningIn -> SignInTriggerState.SigningIn
    currentUser != null -> SignInTriggerState.SignedIn(currentUser)
    lastError != null && lastError != AuthError.Cancelled -> SignInTriggerState.Error(lastError)
    else -> SignInTriggerState.NotSignedIn
}
