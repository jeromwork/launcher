package com.launcher.ui.health

/**
 * Static in-code preset for phone-health thresholds (spec 009 FR-019).
 * Used when `/config.presetOverrides.phoneHealthSettings == null` —
 * the spec-9 default. Read at startup; no runtime mutation.
 *
 * TODO(spec-followup TODO-ARCH-010): загружать override из
 * `/config.presetOverrides.phoneHealthSettings` (spec 012).
 *
 * TODO(spec-followup TODO-ARCH-011): MEDICAL_ / MINIMUM_ / ... presets
 * + selector UI.
 */
data class PhoneHealthPreset(
    val name: String,
    val battery: BatteryThresholds,
    val lastSeen: LastSeenThresholds,
    val audioMutedSeverity: PhoneHealthSeverity,
    val connectivityNoneSeverity: PhoneHealthSeverity,
    val updateCadenceInfoSec: Int,
    val pushAdminOnCritical: Boolean,
) {
    data class BatteryThresholds(
        val warningBelowPercent: Int,
        val criticalBelowPercent: Int,
    )

    data class LastSeenThresholds(
        val warningAfterHours: Int,
        val criticalAfterHours: Int,
    )
}

/**
 * Single source of truth for spec-9 default thresholds (FR-019, plan §11
 * C-3). All scattered literals MUST resolve through this constant.
 */
val DEFAULT_PHONE_HEALTH_PRESET: PhoneHealthPreset = PhoneHealthPreset(
    name = "default",
    battery = PhoneHealthPreset.BatteryThresholds(
        warningBelowPercent = 20,
        criticalBelowPercent = 5,
    ),
    lastSeen = PhoneHealthPreset.LastSeenThresholds(
        warningAfterHours = 1,
        criticalAfterHours = 24,
    ),
    audioMutedSeverity = PhoneHealthSeverity.Warning,
    connectivityNoneSeverity = PhoneHealthSeverity.Warning,
    updateCadenceInfoSec = 30,
    pushAdminOnCritical = false,
)
