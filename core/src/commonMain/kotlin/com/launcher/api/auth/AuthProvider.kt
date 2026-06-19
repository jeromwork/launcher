package com.launcher.api.auth

import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Provider-агностичный порт аутентификации. Domain-уровень знает только об
 * [AuthIdentity], [AuthError] и [Outcome]; никаких слов Firebase, Google,
 * OAuth, Apple, Phone, Email в сигнатурах — Detekt-правило
 * `NoVendorImportsInDomain` (T791, Phase 8) это enforces.
 *
 * Реализации:
 *  - `GoogleSignInAuthAdapter` (androidMain, `realBackend`) — Credential Manager
 *    + Firebase Auth exchange + Firestore identity-links lookup (Phase 5).
 *  - `FakeAuthAdapter` (commonTest, `mockBackend`) — детерминированный, для
 *    тестов и dev-сборок (Phase 2).
 *  - `NoSupportedAuthProvider` — заглушка для устройств без GMS, всегда
 *    возвращает [AuthError.ProviderUnavailable] (Phase 6).
 *
 * Выбор реализации делает `AuthAdapterSelector` (Phase 6) на основе GMS
 * availability и product flavor.
 *
 * Per spec 017 FR-006, contract `auth-provider-port.md`, clarification Q7.
 */
interface AuthProvider {
    /**
     * Текущая identity, реактивный поток. Эмитит:
     *  - `null` начально (до восстановления сессии);
     *  - `null` после выхода / провала refresh;
     *  - [AuthIdentity] после успешного [signIn] или восстановления валидной сессии.
     *
     * Distinct-until-changed — повторные эмиссии одного значения не
     * происходят (per contract).
     */
    val currentUser: Flow<AuthIdentity?>

    /**
     * Запустить flow входа. Provider сам разруливает UI (Credential Manager
     * bottom-sheet, biometric prompt и т.п.).
     *
     * Возвращает:
     *  - [Outcome.Success] с [AuthIdentity] на успехе;
     *  - [Outcome.Failure] с типизированной [AuthError] на любом failure
     *    (включая отмену пользователем — [AuthError.Cancelled]).
     */
    suspend fun signIn(): Outcome<AuthIdentity, AuthError>

    /**
     * Выйти. Идемпотентен: повторный вызов на уже-вышедшем провайдере = no-op.
     * Эмитит `null` в [currentUser].
     */
    suspend fun signOut()
}
