package com.launcher.adapters.wizard.handlers

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler

/**
 * `CheckSpec.AndroidRole` → uses [RoleManager.isRoleHeld] on API 29+;
 * falls back to a CATEGORY_HOME resolveActivity check on API 26-28 (only
 * meaningful for HOME role).
 *
 * Per data-model.md §1.3 / contracts/system-settings-pool-v2.md §4.1.
 */
class AndroidRoleCheckHandler(private val context: Context) : CheckHandler {
    override suspend fun check(spec: CheckSpec): SettingStatus {
        val role = (spec as? CheckSpec.AndroidRole)?.role
            ?: return SettingStatus.NotSupportedOnPlatform
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm?.isRoleHeld(role) == true) SettingStatus.Applied else SettingStatus.NotApplied
        } else if (role == "HOME") {
            // Legacy fallback: probe for our package as the resolved home activity.
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved: ComponentName? = pm.resolveActivity(intent, 0)?.activityInfo
                ?.let { ComponentName(it.packageName, it.name) }
            if (resolved?.packageName == context.packageName) SettingStatus.Applied else SettingStatus.NotApplied
        } else {
            SettingStatus.NotSupportedOnPlatform
        }
    }
}
