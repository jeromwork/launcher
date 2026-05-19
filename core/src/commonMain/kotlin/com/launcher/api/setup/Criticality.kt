package com.launcher.api.setup

/**
 * Whether a [SetupCheck] is essential to the launcher working at all, or just
 * recommended for a better experience (spec 010 FR-017).
 *
 * Drives the Settings badge split (spec 010 FR-019):
 *  - [Required] → red `[!] N критично` (e.g. ROLE_HOME, network).
 *  - [Recommended] → yellow `[?] M рекомендуется` (e.g. battery optimisation, notifications).
 */
sealed class Criticality {
    /** The launcher cannot reliably perform its core duties without this check passing. */
    object Required : Criticality()

    /** Nice-to-have; the launcher still works, but UX degrades. */
    object Recommended : Criticality()
}
