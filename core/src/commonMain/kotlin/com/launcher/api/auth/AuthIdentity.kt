package com.launcher.api.auth

/**
 * Минимальный провайдер-агностичный портрет вошедшего пользователя.
 * Возвращается из [AuthProvider.signIn] и эмитится из [AuthProvider.currentUser].
 *
 * Per spec 017 FR-007 + clarifications Q1 (stableId — наш UUID, не Firebase UID
 * и не Google `sub` claim) и Q4 (поле `providerKind` отсутствует — consumer-у
 * запрещено ветвиться по провайдеру).
 */
data class AuthIdentity(
    val stableId: String,
    val displayName: String?,
    val email: String?,
)
