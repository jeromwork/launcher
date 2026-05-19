package com.launcher.api.setup

/**
 * Outcome of a single [SetupCheck.check] invocation (spec 010 FR-017, FR-020b).
 *
 * - [Ok]: the check passed; nothing to do.
 * - [NotConfigured]: the check did not pass. [reason] is a human-readable
 *   string-resource key or — in the spec 010 FR-020b exception path — the
 *   message of an exception thrown by the underlying system API (e.g. Xiaomi
 *   MIUI `SecurityException` on `PowerManager.isIgnoringBatteryOptimizations`).
 *   Settings UI MUST treat [NotConfigured] from an exception the same as a
 *   normal failure — no crash, badge still updates.
 */
sealed class CheckStatus {
    object Ok : CheckStatus()
    data class NotConfigured(val reason: String) : CheckStatus()
}
