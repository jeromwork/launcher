package com.launcher.adapters.push

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Caching wrapper around Firebase Auth's ID-token (FR-036 §Worker auth).
 *
 *  - Firebase ID-tokens are JWTs with a 1-hour TTL.
 *  - We cache the last token + its expiry-millis and reuse it for up to
 *    [TOKEN_TTL_REUSE_MS] (50 min) so each push doesn't pay an Auth round-trip.
 *  - After the reuse window we call `getIdToken(forceRefresh = true)` to
 *    mint a fresh one.
 *  - On any failure we drop the cache and let the next call retry from scratch.
 *
 * NOT thread-safe across processes (Firebase Auth itself isn't either).
 * In-process serialisation via [Mutex].
 */
class FirebaseTokenSupplier(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private var cachedToken: String? = null
    private var cachedAtMillis: Long = 0

    /** Returns a fresh-or-cached Firebase ID-token. `null` when no user is
     *  signed in (caller should sign in first via [com.launcher.api.identity.IdentityProvider]). */
    suspend fun currentIdToken(): String? = lock.withLock {
        val user = auth.currentUser ?: run {
            cachedToken = null
            return@withLock null
        }
        val cached = cachedToken
        if (cached != null && (nowMillis() - cachedAtMillis) < TOKEN_TTL_REUSE_MS) {
            return@withLock cached
        }
        val fresh = try {
            user.getIdToken(true).await()?.token
        } catch (_: Throwable) {
            null
        }
        if (fresh != null) {
            cachedToken = fresh
            cachedAtMillis = nowMillis()
        }
        fresh
    }

    /** Explicitly drop the cache (e.g. on auth state change). */
    suspend fun invalidate() = lock.withLock {
        cachedToken = null
        cachedAtMillis = 0
    }

    companion object {
        // 50 minutes — leaves 10 minutes of headroom before the actual 1-hour expiry.
        private const val TOKEN_TTL_REUSE_MS: Long = 50 * 60 * 1_000L
    }
}
