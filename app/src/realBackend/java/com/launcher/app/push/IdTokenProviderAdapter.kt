package com.launcher.app.push

import com.launcher.adapters.push.FirebaseTokenSupplier
import family.push.api.IdTokenProvider

/**
 * Adapter: F-4 [FirebaseTokenSupplier] → `:core:push` [IdTokenProvider]. Keeps
 * `:core:push` free of Firebase Auth imports per CLAUDE.md rule 1.
 *
 * [FirebaseTokenSupplier] уже handles caching + force-refresh logic (50-min reuse
 * window, then `getIdToken(true)`). [IdTokenProvider.currentIdToken] просто
 * delegates.
 */
class FirebaseIdTokenProviderAdapter(
    private val tokenSupplier: FirebaseTokenSupplier = FirebaseTokenSupplier(),
) : IdTokenProvider {
    override suspend fun currentIdToken(): String? = tokenSupplier.currentIdToken()
}
