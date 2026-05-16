package com.launcher.fake.config

import com.launcher.api.config.ConfigDiff
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.config.PendingLocalChanges
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [ConfigEditor] для tests. Uses [FakeLocalConfigStore] для pending
 * + an in-memory «server» map для optimistic-concurrency simulation.
 *
 * **No debounce** — tests want deterministic behavior; updateDraft commits
 * immediately to local store. Real DefaultConfigEditor (Phase 4 T062) adds
 * debounce per FR-056.
 *
 * Programmable: [seedServer] sets server-side /config; пуш конкурентно
 * obtainable via [bumpServerUpdatedAt] для simulating «another device wrote
 * while we were editing» case.
 */
class FakeConfigEditor(
    private val localStore: LocalConfigStore,
    private val selfDeviceId: String,
) : ConfigEditor {

    private val mutex = Mutex()
    private val server = mutableMapOf<String, ConfigDocument>()
    private val pendingFlows = mutableMapOf<String, MutableStateFlow<ConfigDocument?>>()

    /** Seed server-side /config — Used Like RemoteSyncBackend в реальном adapter'е. */
    suspend fun seedServer(linkId: String, config: ConfigDocument) {
        mutex.withLock {
            server[linkId] = config
        }
    }

    /** Simulate a concurrent writer bumping server's serverUpdatedAt — для conflict tests. */
    suspend fun bumpServerUpdatedAt(linkId: String, newServerConfig: ConfigDocument) {
        mutex.withLock {
            server[linkId] = newServerConfig
        }
    }

    /** Read server-side /config — для assertions в tests. */
    suspend fun serverConfig(linkId: String): ConfigDocument? = mutex.withLock { server[linkId] }

    override suspend fun updateDraft(
        linkId: String,
        mutator: (ConfigDocument) -> ConfigDocument,
    ) {
        // Start from current pending draft OR current applied OR server config.
        val current = localStore.readPending(linkId)?.draftConfig
            ?: localStore.readAppliedConfig(linkId)
            ?: mutex.withLock { server[linkId] }
            ?: return

        val mutated = mutator(current)
        val snapshotTs = current.serverUpdatedAt
        localStore.writePending(
            linkId,
            PendingLocalChanges(
                linkId = linkId,
                snapshotServerUpdatedAt = snapshotTs,
                draftConfig = mutated,
            ),
        )

        // Reactive flow для UI binding.
        pendingFlows.getOrPut(linkId) { MutableStateFlow(null) }.value = mutated
    }

    override fun pendingDraft(linkId: String): Flow<ConfigDocument?> =
        pendingFlows.getOrPut(linkId) { MutableStateFlow(null) }
            .map { it }

    override suspend fun appliedConfig(linkId: String): ConfigDocument? =
        localStore.readAppliedConfig(linkId)

    override suspend fun pushPending(linkId: String): Outcome<Unit, ConfigSyncError> {
        val pending = localStore.readPending(linkId)
            ?: return Outcome.Success(Unit) // Nothing to push.

        return mutex.withLock {
            val currentServer = server[linkId]
            val currentServerTs = currentServer?.serverUpdatedAt ?: ServerTimestamp.Never

            // FR-013 optimistic concurrency check.
            if (currentServerTs != pending.snapshotServerUpdatedAt) {
                // Conflict — compute diff against fresh server state.
                val diff = if (currentServer != null) {
                    ConfigDiff.compute(local = pending.draftConfig, server = currentServer)
                } else {
                    // No server doc yet, but pending says we read a snapshot —
                    // sufficient mismatch for conflict.
                    ConfigDiff.compute(local = pending.draftConfig, server = pending.draftConfig)
                }
                return@withLock Outcome.Failure(
                    ConfigSyncError.Conflict(
                        localDiff = diff,
                        serverConfig = currentServer ?: pending.draftConfig,
                    )
                )
            }

            // Write succeeds — bump serverUpdatedAt (simulating server-set timestamp).
            val newServerTs = ServerTimestamp(
                epochSeconds = currentServerTs.epochSeconds + 1,
                nanoseconds = 0,
            )
            val pushed = pending.draftConfig.copy(
                serverUpdatedAt = newServerTs,
                lastWriterDeviceId = selfDeviceId,
            )
            server[linkId] = pushed
            localStore.clearPending(linkId)
            pendingFlows[linkId]?.value = null

            Outcome.Success(Unit)
        }
    }

    override suspend fun discardPending(linkId: String) {
        localStore.clearPending(linkId)
        pendingFlows[linkId]?.value = null
    }
}
