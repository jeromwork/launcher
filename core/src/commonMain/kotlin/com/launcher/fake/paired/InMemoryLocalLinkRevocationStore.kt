package com.launcher.fake.paired

import com.launcher.api.paired.LocalLinkRevocationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [LocalLinkRevocationStore] for tests + `mockBackend` flavor
 * (spec 010 T081, CLAUDE.md rule 6 — mock-first).
 *
 * Backed by a single [MutableStateFlow] holding the revoked-id set, so the
 * `isRevoked(linkId)` per-link Flow shares the same upstream and stays
 * consistent across collectors (no stale snapshots).
 */
class InMemoryLocalLinkRevocationStore(
    initial: Set<String> = emptySet(),
) : LocalLinkRevocationStore {

    private val state = MutableStateFlow(initial)

    override suspend fun markRevoked(linkId: String) {
        state.value = state.value + linkId
    }

    override fun isRevoked(linkId: String): Flow<Boolean> =
        state.asStateFlow().map { linkId in it }

    override suspend fun clearRevoked(linkId: String) {
        state.value = state.value - linkId
    }

    override fun revokedLinkIds(): Flow<Set<String>> = state.asStateFlow()

    /** Test hook: synchronous snapshot. */
    fun snapshot(): Set<String> = state.value
}
