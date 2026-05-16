package com.launcher.api.config

import kotlinx.serialization.Serializable

/**
 * Forward-compat placeholder root for per-link preset overrides
 * (spec 009 FR-013). **All fields are null в спеке 9** — type exists today
 * only so adding non-null values в спеке 12 doesn't bump schema version
 * (additive read per spec 008 FR-006).
 *
 * TODO(exit-ramp): фактически пустая структура спека 9; не добавлять fields
 * без current consumer. Schema bump НЕ требуется при добавлении полей
 * (additive policy). См. spec 9 FR-013 + meta-minimization-plan.md W3.
 */
@Serializable
data class PresetSettings(
    val phoneHealthSettings: PhoneHealthSettings? = null,
)
