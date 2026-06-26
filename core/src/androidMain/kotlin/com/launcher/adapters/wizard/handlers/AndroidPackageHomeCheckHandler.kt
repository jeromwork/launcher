package com.launcher.adapters.wizard.handlers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler

/**
 * `CheckSpec.AndroidPackageHome` → checks whether the named package
 * (or our own when `packageName == null`) currently resolves
 * `ACTION_MAIN + CATEGORY_HOME`.
 */
class AndroidPackageHomeCheckHandler(
    private val context: Context,
) : CheckHandler {
    override suspend fun check(spec: CheckSpec): SettingStatus {
        val target = (spec as? CheckSpec.AndroidPackageHome)?.packageName
            ?: context.packageName
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved: ComponentName? = pm.resolveActivity(intent, 0)?.activityInfo
            ?.let { ComponentName(it.packageName, it.name) }
        return if (resolved?.packageName == target) {
            SettingStatus.Applied
        } else {
            SettingStatus.NotApplied
        }
    }
}
