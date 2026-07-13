package com.launcher.preset.model

import kotlinx.serialization.Serializable

/**
 * T016 — WizardPresentation (FR-003, CL-2).
 *
 * Applied once at wizard start; kiosk-mode Theme + StatusBar are applied only after
 * the wizard transitions to home per plan.md §Phase 2 (T056).
 */
@Serializable
data class WizardPresentation(
    val darkMode: Boolean = false,
    val typographyScale: TypographyScale = TypographyScale.Medium,
)
