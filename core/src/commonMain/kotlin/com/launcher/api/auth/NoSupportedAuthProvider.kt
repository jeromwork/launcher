package com.launcher.api.auth

import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [AuthProvider] для устройств без Google Play Services
 * (Huawei после 2019, китайские OEM forks и т.п.).
 *
 *  - [currentUser] всегда эмитит `null`.
 *  - [signIn] всегда возвращает [AuthError.ProviderUnavailable].
 *  - [signOut] — no-op.
 *
 * Выбирается `AuthAdapterSelector` (T761) когда `GoogleApiAvailability`
 * сообщает что GMS недоступны. Consumer'ы видят это через
 * [AuthError.ProviderUnavailable] — в UI это превращается в строку
 * "Вход через Google недоступен на этом устройстве".
 *
 * Per spec 017 FR-018, edge case «Google Play Services недоступен»,
 * OEM matrix Huawei row.
 */
object NoSupportedAuthProvider : AuthProvider {
    override val currentUser: Flow<AuthIdentity?> = flowOf(null)
    override suspend fun signIn(): Outcome<AuthIdentity, AuthError> =
        Outcome.Failure(AuthError.ProviderUnavailable)
    override suspend fun signOut() { /* no-op */ }
}
