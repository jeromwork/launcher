package com.launcher.api.wizard

import com.launcher.api.wizard.data.ConfigDocumentRef

/**
 * Result of a wizard run.
 *
 * `initialConfig` is intentionally a thin reference instead of the spec-008
 * `ConfigDocument` to avoid pulling spec 008 wire-format internals into the
 * domain. Callers (S-1+) translate to whatever they need.
 */
sealed class WizardOutcome {
    data class Completed(
        val initialConfig: ConfigDocumentRef,
        val userPreferences: UserPreferences,
    ) : WizardOutcome()

    data object Cancelled : WizardOutcome()

    data class Failed(val reason: String) : WizardOutcome()
}
