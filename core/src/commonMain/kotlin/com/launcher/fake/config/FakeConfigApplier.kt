package com.launcher.fake.config

import com.launcher.api.config.ConfigApplier
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.link.PartialReason
import com.launcher.api.link.StateApplied
import com.launcher.api.result.Outcome

/**
 * In-memory [ConfigApplier] для tests. Wraps a remote ConfigDocument store +
 * a [LocalConfigStore] + a "self device id" для FR-023 self-as-writer skip.
 *
 * Programmable: callers can [seedRemote] a config that applyFromRemote() будет
 * читать; [forcePartialReasons] simulates apply failures для testing
 * partialApplyReasons (FR-033).
 */
class FakeConfigApplier(
    private val localStore: LocalConfigStore,
    private val selfDeviceId: String,
) : ConfigApplier {

    private val remoteConfigs = mutableMapOf<String, ConfigDocument>()
    private var forcedReasons: List<PartialReason> = emptyList()

    /** Seed «server-side» config that [applyFromRemote] will pick up. */
    fun seedRemote(linkId: String, config: ConfigDocument) {
        remoteConfigs[linkId] = config
    }

    /** Force apply to report these partial reasons (simulates provider unavailable etc.). */
    fun forcePartialReasons(reasons: List<PartialReason>) {
        forcedReasons = reasons
    }

    override suspend fun applyFromRemote(linkId: String): Outcome<StateApplied, ConfigSyncError> {
        val remote = remoteConfigs[linkId]
            ?: return Outcome.Failure(ConfigSyncError.LocalStorageCorrupt(
                IllegalStateException("no remote seeded for $linkId")
            ))

        // FR-023 self-as-writer skip: if this Managed just pushed, avoid double-apply.
        if (remote.lastWriterDeviceId == selfDeviceId) {
            // Still publish StateApplied (idempotent) but skip local write.
            return Outcome.Success(buildState(remote))
        }

        localStore.writeAppliedConfig(linkId, remote)

        if (forcedReasons.isNotEmpty()) {
            return Outcome.Failure(ConfigSyncError.ApplyPartial(forcedReasons))
        }

        return Outcome.Success(buildState(remote))
    }

    private fun buildState(applied: ConfigDocument): StateApplied = StateApplied(
        appliedAt = applied.serverUpdatedAt.epochSeconds * 1000L,
        presetId = applied.presetId,
        fcmToken = null,
        updatedAt = applied.serverUpdatedAt.epochSeconds * 1000L,
        appliedConfigUpdatedAt = applied.serverUpdatedAt,
        flowsApplied = null, // simplified — full mapping в Phase 4
        contactsApplied = null,
        partialApplyReasons = emptyList(),
    )
}
