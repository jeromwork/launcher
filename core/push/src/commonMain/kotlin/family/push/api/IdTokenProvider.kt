package family.push.api

/**
 * Thin SAM port для acquiring current Firebase ID-token (or equivalent JWT).
 *
 * Owned by F-5c push module so that `:core:push` remains standalone (zero
 * project deps per verifyPushIsolation). Actual implementation в app:
 * `AuthIdentityIdTokenProvider` wraps `AuthIdentity.currentIdToken()` (F-4
 * spec 017).
 *
 * Per CLAUDE.md rule 1 (domain isolation) — DefaultPushTrigger не импортирует
 * F-4 types напрямую. Adapter в app module bridges concrete `AuthIdentity` →
 * этот port.
 */
fun interface IdTokenProvider {

    /**
     * Returns currently-valid Firebase ID-token (RS256 signed JWT), or null
     * if user не signed-in. Firebase SDK auto-refreshes near-expiry; caller
     * does NOT cache the result.
     */
    suspend fun currentIdToken(): String?
}
