package com.launcher.api.wizard

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Persistent wizard progress (per FR-003).
 *
 * Wire format — schemaVersion=1 per CLAUDE.md rule 5. Persisted via
 * [WizardCheckpointStore] (DataStore on Android, in-memory in tests).
 *
 * Bumped schemaVersion handling: load with schemaVersion > known → engine
 * treats as invalid → starts from step 0 (graceful, no crash). FR-003.
 */
@Deprecated(
    "Superseded by TASK-120 Profile persistence (via com.launcher.preset.port.ProfileStore + preWizardSnapshot). Removal scheduled for the draft-1 wizard refactor.",
)
@Serializable
data class WizardCheckpoint(
    val schemaVersion: Int = 1,
    val manifestId: String,
    val currentStepIndex: Int,
    val answers: Map<String, JsonElement>,
)

interface WizardCheckpointStore {
    suspend fun load(manifestId: String): WizardCheckpoint?
    suspend fun save(checkpoint: WizardCheckpoint)
    suspend fun clear(manifestId: String)
}
