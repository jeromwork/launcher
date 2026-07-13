package cryptokit.keys.api

/**
 * Минимальный провайдер-агностичный портрет вошедшего пользователя для F-5.
 *
 * Same shape как `com.launcher.api.auth.AuthIdentity` (spec 017 F-4) — локальная
 * копия в `cryptokit.keys.*` чтобы `:core:keys` оставался slim. Mapping F-4
 * AuthIdentity ↔ F-5 AuthIdentity делается в app-layer adapter
 * (GoogleSignInIdentityProof) per CLAUDE.md rule 2 (ACL для каждой external dep,
 * в т.ч. между нашими модулями).
 *
 * TODO(refactor-when-3rd-consumer): см. Outcome.kt — extract в `:core:common`.
 */
data class AuthIdentity(
    val stableId: String,
    val displayName: String?,
    val email: String?,
)
