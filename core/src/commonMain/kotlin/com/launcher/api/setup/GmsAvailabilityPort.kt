package com.launcher.api.setup

/**
 * Port (interface) abstracting the Google Play Services availability check
 * (spec 010 FR-042..FR-044).
 *
 * Domain code MUST depend only on this port — never on
 * `com.google.android.gms.common.GoogleApiAvailability` directly (CLAUDE.md
 * rule 1 «domain isolated from infrastructure», enforced by Konsist gate
 * [Spec010IsolationTest.T007_api_setup_isolation]).
 *
 * Implementations:
 *  - real ([com.launcher.adapters.setup.GmsAvailabilityAdapter], androidMain):
 *    wraps `GoogleApiAvailability.isGooglePlayServicesAvailable()` and maps the
 *    `ConnectionResult` integer codes to the sealed [GmsStatus].
 *  - fake (commonTest `FakeGmsAvailabilityPort`): returns a programmable status
 *    for unit and Compose tests.
 *
 * Called once at FirstLaunchActivity entry (FR-042) before the setup wizard.
 * Result drives routing: [GmsStatus.Available] → wizard; [GmsStatus.MissingRecoverable]
 * → system resolution dialog (FR-044); [GmsStatus.MissingFatal] → senior-safe
 * hard-block screen (FR-042 / FR-043) + finishAffinity().
 */
interface GmsAvailabilityPort {
    /**
     * Suspend-friendly query for current GMS state. May briefly do disk / IPC
     * work inside the adapter (GoogleApiAvailability touches package manager),
     * so callers should not invoke it on the UI thread without a coroutine.
     */
    suspend fun status(): GmsStatus
}
