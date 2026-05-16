package com.launcher.adapters.config

import com.launcher.api.config.ConfigDiff
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigDocumentWireFormat
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.ConfigSyncConstants
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.config.PendingLocalChanges
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.RemoteSyncBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Production [ConfigEditor] для realBackend flavor (spec 008 Phase 4 T062).
 *
 * Uses [RemoteSyncBackend.runTransaction] for FR-013 optimistic-concurrency:
 * transaction reads /config/current, checks serverUpdatedAt match against
 * client's snapshot, writes with new server-timestamp ИЛИ aborts.
 *
 * Autosave (FR-056): updateDraft debounces по AUTOSAVE_DEBOUNCE_MS (300ms)
 * within a coroutine scope. Caller injects [scope] (application-scope —
 * survives Activity recreation, plan.md state-management CHK010).
 */
class DefaultConfigEditor(
    private val remoteSync: RemoteSyncBackend,
    private val localStore: LocalConfigStore,
    private val selfDeviceIdProvider: () -> String,
    private val scope: CoroutineScope,
) : ConfigEditor {

    private val pendingFlows = mutableMapOf<String, MutableStateFlow<ConfigDocument?>>()
    private val debounceJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()

    override suspend fun updateDraft(
        linkId: String,
        mutator: (ConfigDocument) -> ConfigDocument,
    ) {
        // Read current base: pending → applied → fail (need at least applied).
        val base = localStore.readPending(linkId)?.draftConfig
            ?: localStore.readAppliedConfig(linkId)
            ?: return // No base yet — caller must apply before edit.

        val draft = mutator(base)
        val snapshot = base.serverUpdatedAt

        // Update reactive flow immediately for responsive UI.
        getOrCreateFlow(linkId).value = draft

        // Debounce the SQLDelight write (FR-056: ~300ms).
        mutex.withLock {
            debounceJobs[linkId]?.cancel()
            debounceJobs[linkId] = scope.launch {
                delay(ConfigSyncConstants.AUTOSAVE_DEBOUNCE_MS)
                localStore.writePending(
                    linkId = linkId,
                    pending = PendingLocalChanges(
                        linkId = linkId,
                        snapshotServerUpdatedAt = snapshot,
                        draftConfig = draft,
                    ),
                )
            }
        }
    }

    override fun pendingDraft(linkId: String): Flow<ConfigDocument?> =
        getOrCreateFlow(linkId).asStateFlow()

    override suspend fun appliedConfig(linkId: String): ConfigDocument? =
        localStore.readAppliedConfig(linkId)

    override suspend fun pushPending(linkId: String): Outcome<Unit, ConfigSyncError> {
        // Flush any pending debounce.
        mutex.withLock { debounceJobs[linkId] }?.join()

        val pending = localStore.readPending(linkId)
            ?: return Outcome.Success(Unit) // Nothing to push.

        val configPath = DocPath.LinkConfig(linkId)

        val txResult: Outcome<Outcome<Unit, ConfigSyncError>, BackendError> =
            remoteSync.runTransaction {
                // Inside transaction: read latest server state, compare to snapshot.
                val serverSnapshot = get(configPath)
                val serverConfig = serverSnapshot?.let { snap ->
                    val obj = snap.data as? JsonObject ?: return@let null
                    (ConfigDocumentWireFormat.deserialize(obj) as? Outcome.Success)?.value
                }

                val currentServerTs = serverConfig?.serverUpdatedAt ?: ServerTimestamp.Never

                if (currentServerTs != pending.snapshotServerUpdatedAt) {
                    // FR-013 conflict: server advanced между read и push.
                    val diff = if (serverConfig != null) {
                        ConfigDiff.compute(local = pending.draftConfig, server = serverConfig)
                    } else {
                        ConfigDiff() // No server doc — empty diff (caller treats as conflict anyway).
                    }
                    return@runTransaction Outcome.Failure(
                        ConfigSyncError.Conflict(
                            localDiff = diff,
                            serverConfig = serverConfig ?: pending.draftConfig,
                        )
                    )
                }

                // No conflict — write с self as last writer; serverUpdatedAt будет
                // overwritten by FirebaseRemoteSyncBackend's `updatedAt` field на write.
                // Note: serialize includes whatever serverUpdatedAt we put; server will
                // overwrite via FieldValue.serverTimestamp(). We use a placeholder.
                val pushedConfig = pending.draftConfig.copy(
                    lastWriterDeviceId = selfDeviceIdProvider(),
                    // serverUpdatedAt placeholder — adapter writes server timestamp.
                    serverUpdatedAt = ServerTimestamp.Never,
                )
                val encoded = ConfigDocumentWireFormat.serialize(pushedConfig)
                set(path = configPath, data = encoded, schemaVersion = pushedConfig.schemaVersion)
                Outcome.Success(Unit)
            }

        // Unwrap nested Outcome: transaction-level failure vs business logic failure.
        return when (txResult) {
            is Outcome.Success -> {
                val inner = txResult.value
                if (inner is Outcome.Success) {
                    localStore.clearPending(linkId)
                    getOrCreateFlow(linkId).value = null
                }
                inner
            }
            is Outcome.Failure -> Outcome.Failure(ConfigSyncError.BackendFailure(txResult.error))
        }
    }

    override suspend fun discardPending(linkId: String) {
        mutex.withLock { debounceJobs[linkId] }?.cancel()
        localStore.clearPending(linkId)
        getOrCreateFlow(linkId).value = null
    }

    private fun getOrCreateFlow(linkId: String): MutableStateFlow<ConfigDocument?> =
        pendingFlows.getOrPut(linkId) { MutableStateFlow(null) }
}
