package com.launcher.app.data.identity

import family.push.api.IdTokenProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TASK-119 (2026-07-09) — [IdTokenProvider] backed by our HS256 JWT issued
 * by auth-worker's `/auth/exchange`. Injected into
 * [com.launcher.app.data.recovery.WorkerRecoveryKeyBackup] instead of the
 * Firebase-token-supplying adapter, so backup calls carry OUR JWT (whose
 * `sub` is the caller's stableId), not the Firebase JWT (whose `sub` is
 * the Firebase uid).
 *
 * **Cache policy**: reuses the token until 60 seconds before its
 * `expiresAt`, then re-runs `/auth/exchange` to mint a fresh one. This
 * matches the reuse-window pattern of [com.launcher.adapters.push.FirebaseTokenSupplier]
 * for symmetry (50-min window on a 60-min token).
 *
 * **`invalidate()`**: not needed — expiry-based refresh handles both
 * natural rotation and the pre-2026-07-09 claim-propagation edge case
 * (which no longer exists on this path — our JWT is minted synchronously
 * with the correct `sub` on issuance).
 *
 * mockBackend flavor binds a no-op that returns null (no cloud, no probe).
 */
class OurJwtProvider(
    private val exchangeClient: AuthTokenExchangeClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : IdTokenProvider {

    private val lock = Mutex()
    private var cachedToken: String? = null
    private var cachedExpiresAt: Long = 0L

    override suspend fun currentIdToken(): String? = lock.withLock {
        val nowMs = nowMillis()
        val cached = cachedToken
        if (cached != null && cachedExpiresAt - nowMs > REFRESH_HEADROOM_MS) {
            return@withLock cached
        }
        when (val r = exchangeClient.exchange()) {
            is ExchangeResult.Success -> {
                cachedToken = r.token
                cachedExpiresAt = r.expiresAt
                r.token
            }
            else -> null
        }
    }

    /**
     * Drop the cached JWT; the next [currentIdToken] call will re-run
     * `/auth/exchange`. Exposed only for future scenarios (explicit sign-out,
     * observed 401 from a downstream Worker). Not called by TASK-119 code.
     */
    suspend fun invalidate() = lock.withLock {
        cachedToken = null
        cachedExpiresAt = 0L
    }

    companion object {
        /** Refresh 60 seconds before natural expiry. */
        private const val REFRESH_HEADROOM_MS: Long = 60_000L
    }
}
