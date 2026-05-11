package com.launcher.api.link

/**
 * Initial snapshot written to `/links/{linkId}/state/current` when the Managed
 * user accepts the consent screen (spec 007 §FR-009, contracts/state-bootstrap.md).
 *
 * **Bootstrap-only** schema for spec 007. The full state schema (flows, slots,
 * applied capabilities) is added in spec 008 as **additive** fields —
 * [SCHEMA_VERSION] stays at `1` until a rename/removal is required.
 *
 *  - [fcmToken]: `null` when GMS is unavailable (C13 stub); FCM push delivery
 *    falls back to the in-app banner.
 *  - [appliedAt]/[updatedAt]: epoch millis; server-set on write.
 */
data class LinkBootstrap(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appliedAt: Long,
    val presetId: String,
    val fcmToken: String?,
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}
