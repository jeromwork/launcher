package com.launcher.fake.link

import com.launcher.api.link.Link
import com.launcher.api.link.LinkRegistry
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [LinkRegistry] for tests and `mockBackend` flavor (FR-012).
 *
 * Tests typically seed the registry via [seedLink] before exercising a code
 * path that expects an existing pairing, then assert via [currentLink].
 *
 * [activate] is a no-op here because the registry has no way to fabricate a
 * full [Link] from a [linkId] alone (admin identity is unknown); production
 * code uses `FirestoreLinkRegistry` which reads from `/links/{linkId}`. Tests
 * that need post-activate state should call [seedLink] directly.
 */
class FakeLinkRegistry(
    initial: Link? = null,
) : LinkRegistry {

    private val state = MutableStateFlow(initial)

    override fun currentLink(): Flow<Link?> = state.asStateFlow()

    override suspend fun activate(linkId: String): Outcome<Link, BackendError> {
        // No-op in Fake — see kdoc. Tests that need to simulate activate should
        // call seedLink with a fully-formed Link first; if the registry is empty
        // when activate is called, surface NotFound so the contract stays close
        // to the real adapter (which would 404 on a missing /links/{linkId}).
        val current = state.value
        if (current != null && current.linkId == linkId) {
            return Outcome.Success(current)
        }
        return Outcome.Failure(BackendError.NotFound)
    }

    override suspend fun revoke(): Outcome<Unit, BackendError> {
        state.value = null
        return Outcome.Success(Unit)
    }

    // ---- Test hooks ------------------------------------------------------

    fun seedLink(link: Link?) {
        state.value = link
    }

    fun snapshot(): Link? = state.value
}
