package family.keys.api

import kotlinx.coroutines.flow.Flow

/**
 * Абстракция над F-4 AuthProvider для нужд F-5 (FR-006, FR-007, FR-028).
 *
 * **Why это отдельный port, а не direct use F-4 AuthProvider**:
 *  • CLAUDE.md rule 2 (ACL): F-5 не должен ссылаться на типы из F-4 модуля напрямую.
 *  • F-5 нужен minimum subset (только current identity + Sign-In/Out trigger),
 *    F-4 AuthProvider шире (handles auth flow UI state).
 *
 * Adapter (GoogleSignInIdentityProof) живёт в app-layer, мостит F-4 → F-5.
 *
 * Per contracts/identity-proof-v1.md.
 */
interface IdentityProof {
    /**
     * Текущая identity или null если не signed in. Synchronous snapshot.
     */
    suspend fun currentIdentity(): AuthIdentity?

    /**
     * Hot flow identity состояния. Emit'ит каждый раз когда identity меняется
     * (Sign-In, Sign-Out, token refresh с новым UID).
     */
    val identityFlow: Flow<AuthIdentity?>

    /**
     * Запускает Sign-In flow. Suspends до завершения или cancellation.
     */
    suspend fun requestSignIn(): Outcome<AuthIdentity, IdentityError>

    /**
     * Sign-Out из identity provider. НЕ удаляет local keys — это отдельный
     * вызов [RootKeyManager.wipe].
     */
    suspend fun signOut(): Outcome<Unit, IdentityError>
}
