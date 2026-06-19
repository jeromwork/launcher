package com.launcher.api.auth

/**
 * Provider-agnostic sign-in failure classes. Consumers pattern-match exhaustively
 * to drive UI without ever importing vendor SDK exception types.
 *
 * Per spec 017 FR-009 and clarification Q3 (no `Unknown` swallowing PII —
 * `message` carries only sanitised category text).
 */
sealed class AuthError {
    object NetworkError : AuthError()
    object Cancelled : AuthError()
    object NoEmail : AuthError()
    object ProviderUnavailable : AuthError()
    data class Unknown(val message: String) : AuthError()
}
