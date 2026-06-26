package com.launcher.adapters.wizard.handlers

import android.content.Context
import android.os.PowerManager
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler

/**
 * `CheckSpec.AndroidSpecialPermission` → per-variant dispatch.
 * Currently supports `ignore_battery_optimizations`; unknown variants
 * return [SettingStatus.Indeterminate] (graceful — see Article VII §15).
 */
class AndroidSpecialPermissionCheckHandler(
    private val context: Context,
) : CheckHandler {
    override suspend fun check(spec: CheckSpec): SettingStatus {
        val variant = (spec as? CheckSpec.AndroidSpecialPermission)?.variant
            ?: return SettingStatus.NotSupportedOnPlatform
        return when (variant) {
            "ignore_battery_optimizations" -> {
                val pm = context.getSystemService(PowerManager::class.java)
                runCatching {
                    if (pm?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                        SettingStatus.Applied
                    } else {
                        SettingStatus.NotApplied
                    }
                }.getOrElse {
                    // OEM quirk (Xiaomi MIUI throws SecurityException) → Indeterminate.
                    SettingStatus.Indeterminate
                }
            }
            else -> SettingStatus.Indeterminate
        }
    }
}
