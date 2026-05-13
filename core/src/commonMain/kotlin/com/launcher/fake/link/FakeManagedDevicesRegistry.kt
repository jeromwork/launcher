package com.launcher.fake.link

import com.launcher.api.link.Link
import com.launcher.api.link.ManagedDevicesRegistry
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [ManagedDevicesRegistry] for tests and `mockBackend` flavor.
 * Mirrors the contract of [com.launcher.adapters.link.FirestoreManagedDevicesRegistry]
 * without a real Firestore listener.
 */
class FakeManagedDevicesRegistry(
    initial: List<Link> = emptyList(),
) : ManagedDevicesRegistry {

    private val state = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Link>> = state.asStateFlow()

    override fun recordClaim(link: Link) {
        if (state.value.any { it.linkId == link.linkId }) return
        state.value = state.value + link
    }

    override fun forgetLink(linkId: String) {
        state.value = state.value.filterNot { it.linkId == linkId }
    }

    override suspend fun findByManagedDeviceId(
        managedDeviceId: String,
    ): Outcome<Link?, BackendError> =
        Outcome.Success(state.value.firstOrNull { it.managedDeviceId == managedDeviceId })

    override fun debugEvents(): Flow<List<String>> = MutableStateFlow(emptyList())

    // ---- Test hooks ------------------------------------------------------

    fun seed(links: List<Link>) {
        state.value = links
    }

    fun snapshot(): List<Link> = state.value
}
