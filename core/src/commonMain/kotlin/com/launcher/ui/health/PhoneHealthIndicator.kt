package com.launcher.ui.health

import com.launcher.api.apps.IconRef

/**
 * ViewModel-emitted row representing one indicator chip on the phone
 * health screen (spec 009 FR-017, FR-A11Y-001). Local UI type — never
 * crosses wire boundary.
 *
 * [sourceType] is `"phone"` в спеке 9; forward-compat for federated
 * devices (`"watch"`, `"sensor"`) in future specs.
 */
data class PhoneHealthIndicator(
    val id: String,
    val sourceType: String = "phone",
    val label: String,
    val value: String,
    val severity: PhoneHealthSeverity,
    val iconRes: IconRef,
    val contentDescription: String,
    val updatedAt: Long,
)
