package com.launcher.fake.config

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.config.PendingLocalChanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [LocalConfigStore] for tests + future mockBackend flavor wiring.
 * Per CLAUDE.md §6 — fake adapter parity with future SqlDelightLocalConfigStore.
 *
 * Thread-safe via [Mutex]; `pendingLinks()` exposes a reactive Flow для UI binding tests.
 */
class FakeLocalConfigStore : LocalConfigStore {

    private val mutex = Mutex()

    private val appliedConfigs = mutableMapOf<String, ConfigDocument>()
    private val pendingChanges = mutableMapOf<String, PendingLocalChanges>()
    private val pendingLinksFlow = MutableStateFlow<Set<String>>(emptySet())
    // Spec 010 T029 — Flow over applied config per link.
    private val appliedConfigFlows = mutableMapOf<String, MutableStateFlow<ConfigDocument?>>()

    override suspend fun readAppliedConfig(linkId: String): ConfigDocument? =
        mutex.withLock { appliedConfigs[linkId] }

    override fun observeAppliedConfig(linkId: String): Flow<ConfigDocument?> =
        appliedConfigFlows.getOrPut(linkId) {
            MutableStateFlow(appliedConfigs[linkId])
        }.asStateFlow()

    override suspend fun writeAppliedConfig(linkId: String, config: ConfigDocument) {
        mutex.withLock {
            appliedConfigs[linkId] = config
            appliedConfigFlows.getOrPut(linkId) { MutableStateFlow(null) }.value = config
        }
    }

    override suspend fun readPending(linkId: String): PendingLocalChanges? =
        mutex.withLock { pendingChanges[linkId] }

    override suspend fun writePending(linkId: String, pending: PendingLocalChanges) {
        mutex.withLock {
            pendingChanges[linkId] = pending
            pendingLinksFlow.value = pendingChanges.keys.toSet()
        }
    }

    override suspend fun clearPending(linkId: String) {
        mutex.withLock {
            pendingChanges.remove(linkId)
            pendingLinksFlow.value = pendingChanges.keys.toSet()
        }
    }

    override fun pendingLinks(): Flow<Set<String>> = pendingLinksFlow.map { it.toSet() }
}
