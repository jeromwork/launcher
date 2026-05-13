package com.launcher.api.identity

/** Discriminated error surface for [IdentityProvider]. Adapter MUST translate
 *  every vendor exception (`FirebaseAuthException`, `IOException`) into one
 *  of these before crossing into commonMain (CLAUDE.md §2). */
sealed interface IdentityError {
    data object NetworkUnavailable : IdentityError
    data object QuotaExceeded : IdentityError
    data class Unknown(val message: String) : IdentityError
}
