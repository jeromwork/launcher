package com.launcher.api.setup

/**
 * Domain-level outcome of [GmsAvailabilityPort.status] (spec 010 FR-042..FR-044).
 *
 * - [Available]: GMS is installed, enabled, and at a supported version. The
 *   launcher's GMS-dependent features (Firestore, FCM push) may proceed.
 *
 * - [MissingRecoverable]: GMS is installed but in a state the user can fix
 *   (out-of-date, disabled, updating). FR-044: launcher shows the system
 *   resolution dialog via the adapter and stays running.
 *
 * - [MissingFatal]: GMS is not installed or fundamentally unsupported
 *   on this device (no Google services, e.g. Huawei without GMS).
 *   FR-042 / FR-043: launcher hard-blocks with a senior-safe full-screen
 *   explanation and finishes.
 *
 * The split into recoverable / fatal happens inside the adapter
 * (mapping `GoogleApiAvailability.ConnectionResult` codes); domain code only
 * matches on the sealed variants — never on raw integers.
 */
sealed class GmsStatus {
    object Available : GmsStatus()

    data class MissingRecoverable(
        /** Human-readable reason; localised string-resource key, never an exception message. */
        val reason: String,
        /** Whether the underlying SDK exposes a resolution Intent for FR-044 system dialog. */
        val resolutionAvailable: Boolean,
    ) : GmsStatus()

    data class MissingFatal(
        /** Human-readable reason; localised string-resource key, never an exception message. */
        val reason: String,
    ) : GmsStatus()
}
