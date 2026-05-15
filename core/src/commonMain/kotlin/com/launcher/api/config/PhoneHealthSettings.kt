package com.launcher.api.config

import kotlinx.serialization.Serializable

/**
 * Wire-format projection of `PhoneHealthPreset` (UI type — see
 * `ui/health/PhoneHealthPreset.kt`). Lives in domain layer because it
 * appears в `/config/current` payload (forward-compat — not actually
 * written в спеке 9). [SeverityWire] is a **separate enum from UI**
 * `PhoneHealthSeverity` to keep Compose-side enum ordinals decoupled
 * from wire-format ordering (CLAUDE.md rule 1).
 *
 * TODO(exit-ramp): не загружается в спеке 9; UI читает
 * DEFAULT_PHONE_HEALTH_PRESET; spec 012 будет грузить отсюда.
 *
 * TODO(spec-followup TODO-ARCH-010): inject preset через DI вместо
 * DEFAULT_ константы.
 */
@Serializable
data class PhoneHealthSettings(
    val batteryWarningPercent: Int,
    val batteryCriticalPercent: Int,
    val lastSeenWarningHours: Int,
    val lastSeenCriticalHours: Int,
    val audioMutedSeverity: SeverityWire,
    val connectivityNoneSeverity: SeverityWire,
    val updateCadenceInfoSec: Int,
    val pushAdminOnCritical: Boolean,
)

/**
 * Wire-format severity enum. Closed set; reader fails closed on unknown
 * values (FR-043). Separate from UI `PhoneHealthSeverity` per plan §11 C-5.
 */
@Serializable
enum class SeverityWire(val wireValue: String) {
    Info(wireValue = "info"),
    Warning(wireValue = "warning"),
    Critical(wireValue = "critical"),
}
