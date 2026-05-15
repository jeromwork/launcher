package com.launcher.api.config

import kotlinx.serialization.Serializable

/**
 * Immutable historical record of a `/config/current` state — written every
 * time an admin pushes from EditorScreen (spec 009 FR-036). Persisted in
 * Firestore subcollection `/links/{linkId}/config/history/{autoId}`. Pure
 * data; no behaviour.
 *
 * Dual schemaVersion (envelope + nested config) per plan.md §11 C-2 +
 * research.md R-002 — independent evolution: envelope changes (e.g.
 * adding [recordedFromDeviceId] semantics) don't require bumping config
 * schemaVersion and vice-versa.
 *
 * @property snapshotSchemaVersion ≥ 1. Reader does NOT throw on > 1
 *   (FR-043 carried forward — `SnapshotMigrator` handles failure).
 * @property config Spec 008 `ConfigDocument` — carries own schemaVersion.
 * @property recordedAt Epoch millis. Server-side `serverTimestamp()`
 *   mapped on read.
 * @property recordedFromDeviceId Anti-spoof: server-enforced equal to
 *   `request.auth.uid` via Firestore Rule (FR-045a).
 */
@Serializable
data class ConfigSnapshot(
    val snapshotSchemaVersion: Int = SUPPORTED_SNAPSHOT_SCHEMA_VERSION,
    val config: ConfigDocument,
    val recordedAt: Long,
    val recordedFromDeviceId: String,
) {
    companion object {
        const val SUPPORTED_SNAPSHOT_SCHEMA_VERSION: Int = 1
    }
}
