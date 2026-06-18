package com.launcher.api.auth

/**
 * Полный domain-портрет пользователя. В отличие от [AuthIdentity] (lean
 * AuthProvider output), [User] объединяет identity + ключи F-5 + subscription.
 *
 * В F-4 path всегда:
 *  - `identityKeys = null` (F-5 ещё не ship'нулся);
 *  - `subscriptionState = SubscriptionState.Unknown` (S-10 server JWT ещё нет).
 *
 * Per spec 017 FR-010 + clarification Q4 (no `providerKind` field).
 */
data class User(
    val id: String,
    val identityKeys: IdentityKeys?,
    val email: String?,
    val displayName: String?,
    val subscriptionState: SubscriptionState,
)
