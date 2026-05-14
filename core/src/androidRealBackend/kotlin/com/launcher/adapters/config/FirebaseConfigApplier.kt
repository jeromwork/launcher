package com.launcher.adapters.config

import com.launcher.api.config.ConfigApplier
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigDocumentWireFormat
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.link.PartialReason
import com.launcher.api.link.StateApplied
import com.launcher.api.link.StateAppliedWireFormat
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.RemoteSyncBackend
import kotlinx.serialization.json.JsonObject

/**
 * Production [ConfigApplier] для realBackend flavor (spec 008 Phase 4 T060).
 *
 * Read /config/current → atomically write to local SQLDelight → publish
 * /state/current с appliedConfigUpdatedAt for admin UI «Применено на телефоне ✓»
 * indicator (SC-001b).
 *
 * FR-023 self-as-writer skip: if this Managed-as-editor just pushed (the
 * `lastWriterDeviceId` echoes back to ourselves), skip local re-write since
 * apply already happened pre-push.
 */
class FirebaseConfigApplier(
    private val remoteSync: RemoteSyncBackend,
    private val localStore: LocalConfigStore,
    private val selfDeviceIdProvider: () -> String,
) : ConfigApplier {

    override suspend fun applyFromRemote(linkId: String): Outcome<StateApplied, ConfigSyncError> {
        val configPath = DocPath.LinkConfig(linkId)

        // Read /config/current.
        val readResult = remoteSync.readDoc(configPath)
        val snapshot = when (readResult) {
            is Outcome.Success -> readResult.value
            is Outcome.Failure -> return Outcome.Failure(ConfigSyncError.BackendFailure(readResult.error))
        } ?: return Outcome.Failure(ConfigSyncError.BackendFailure(BackendError.NotFound))

        // Decode wire format.
        val configElement = snapshot.data as? JsonObject
            ?: return Outcome.Failure(ConfigSyncError.BackendFailure(
                BackendError.Unknown("config payload not a JsonObject")
            ))
        val config = when (val r = ConfigDocumentWireFormat.deserialize(configElement)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(ConfigSyncError.BackendFailure(r.error))
        }

        val selfId = selfDeviceIdProvider()

        // FR-023 self-as-writer skip: if WE pushed, local store already has it.
        val skipLocalWrite = config.lastWriterDeviceId == selfId

        if (!skipLocalWrite) {
            try {
                localStore.writeAppliedConfig(linkId, config)
            } catch (t: Throwable) {
                return Outcome.Failure(ConfigSyncError.LocalStorageCorrupt(t))
            }
        }

        // Build StateApplied and publish to /state/current.
        // Note: spec 008 FlowApplied/ContactApplied population is simplified —
        // full provider-availability check будет в Phase 8 wiring when Settings
        // / FlowRepository merger integrates с real providers. Today we
        // optimistically mark all as appliedSuccessfully=true (no real provider
        // check yet).
        val state = StateApplied(
            appliedAt = System.currentTimeMillis(),
            presetId = config.presetId,
            fcmToken = null, // FCM token publication is handled by spec 007 elsewhere.
            updatedAt = System.currentTimeMillis(),
            appliedConfigUpdatedAt = config.serverUpdatedAt,
            flowsApplied = null,    // Populated in Phase 8 wiring task.
            contactsApplied = null, // Populated in Phase 8 wiring task.
            partialApplyReasons = emptyList(),
        )

        val stateJson = StateAppliedWireFormat.serialize(state)
        val statePath = DocPath.LinkState(linkId)
        val stateWriteResult = remoteSync.writeDoc(
            path = statePath,
            data = stateJson,
            schemaVersion = state.schemaVersion,
        )
        when (stateWriteResult) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> return Outcome.Failure(ConfigSyncError.BackendFailure(stateWriteResult.error))
        }

        return Outcome.Success(state)
    }
}
