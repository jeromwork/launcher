package com.launcher.app.data.identity

/**
 * Port for invalidating any process-local Firebase ID-token cache after an
 * out-of-band claims update (e.g. identity worker's `setCustomAttributes`
 * call). The next [family.push.api.IdTokenProvider.currentIdToken] invocation
 * must hit the Firebase Auth back-end to fetch a fresh JWT carrying the new
 * `claims.stableId`.
 *
 * Bound in `realBackend` to `FirebaseTokenSupplier::invalidate`. The
 * `mockBackend` flavor binds a no-op (the fake auth adapter doesn't cache
 * tokens — or have any concept of custom claims — so invalidation is moot).
 *
 * task-6 wiring 2026-06-30 (T681-FOLLOWUP closure).
 */
fun interface IdentityCacheInvalidator {
    suspend fun invalidate()
}
