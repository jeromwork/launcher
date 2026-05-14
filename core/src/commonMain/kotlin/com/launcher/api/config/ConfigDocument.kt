package com.launcher.api.config

import kotlinx.serialization.Serializable

/**
 * Wire-format root for `/links/{linkId}/config/current` (spec 008 §FR-001..006,
 * contracts/config.md).
 *
 * Schema invariants:
 *  - [schemaVersion] is read first by the deserializer (wire-format CHK002) и
 *    cannot decrease на update (Security Rules + FR-006).
 *  - [serverUpdatedAt] is server-set via `FieldValue.serverTimestamp()` — clients
 *    capture it as their snapshot для optimistic-concurrency precondition (FR-002,
 *    FR-012, FR-013).
 *  - [lastWriterDeviceId] identifies which editor (admin-phone / admin-tablet /
 *    Managed-phone) wrote the document last. Lives **only** в /config; **NOT
 *    mirrored** в /state to prevent voyeurism (security CHK019).
 *  - Each element ([flows], [slots] inside flows, [contacts]) has a UUID v4 [ElementId]
 *    so diff/merge can match по identity, не by position (FR-004).
 *
 * Backward-compat policy (FR-006): adding optional fields is additive (no version
 * bump); rename/remove requires bump → 2 + reader-migration в next spec.
 *
 * Forward-compat handling: spec 008 ships v=1 only; admin-v2 ↔ Managed-v1
 * mismatch handled by future spec `app-version-compatibility` (OUT-006).
 */
@Serializable
data class ConfigDocument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val serverUpdatedAt: ServerTimestamp,
    val lastWriterDeviceId: String,
    val presetId: String,
    val flows: List<Flow>,
    val contacts: List<Contact>,
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}
