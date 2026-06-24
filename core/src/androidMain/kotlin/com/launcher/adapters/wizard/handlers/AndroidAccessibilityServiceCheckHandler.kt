package com.launcher.adapters.wizard.handlers

import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler

/**
 * `CheckSpec.AndroidAccessibilityService` → always returns
 * [SettingStatus.Indeterminate]. Programmatic detection of an enabled
 * accessibility service is unreliable across OEMs (Samsung KNOX,
 * Xiaomi MIUI restrictions), so this handler defers to the
 * SelfAttest path (user confirms manually in-app).
 */
class AndroidAccessibilityServiceCheckHandler : CheckHandler {
    override suspend fun check(spec: CheckSpec): SettingStatus {
        if (spec !is CheckSpec.AndroidAccessibilityService) {
            return SettingStatus.NotSupportedOnPlatform
        }
        return SettingStatus.Indeterminate
    }
}
