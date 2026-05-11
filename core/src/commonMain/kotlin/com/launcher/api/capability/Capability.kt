package com.launcher.api.capability

import com.launcher.api.action.ProviderId
import kotlinx.serialization.Serializable

/**
 * Per-provider snapshot of "what this device can do".
 *
 * Wire-format root per [`contracts/capability-wire-format.md`](specs/006-provider-capabilities-and-health/contracts/capability-wire-format.md)
 * v1.0.0. The shape is **public**: persisted in app-private DataStore today,
 * exported to Firestore `/links/{linkId}/capabilities` in spec 007. Any change
 * here is a wire-format change.
 *
 * Invariants:
 *  - [schemaVersion] >= 1; readers accept > [SUPPORTED_SCHEMA_VERSION] with
 *    best-effort parse (FR-043, aligns with spec 005 Clarification C1).
 *  - [iconId] uses namespace-prefixed convention `<namespace>:<name>` per
 *    [`contracts/icon-id-namespace.md`](specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md).
 *    Validation NOT enforced in `init` — forward-compat with future namespaces
 *    requires unknown values to parse without exception (resolved at consume
 *    time by [IconStorage]).
 *  - [versionCode] is `Long?` (PackageInfoCompat returns Long); null when
 *    provider is not installed OR when adapter cannot determine it (iOS, etc.).
 */
@Serializable
data class Capability(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
    val providerId: ProviderId,
    val displayName: String,
    val iconId: String,
    val iconSha256: String? = null,
    val available: Boolean,
    val versionCode: Long? = null,
) {
    companion object {
        /** Wire-format version recognised by this build. Reader does NOT throw on > this (FR-043). */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
    }
}
