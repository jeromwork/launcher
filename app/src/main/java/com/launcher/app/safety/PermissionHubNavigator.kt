package com.launcher.app.safety

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object PermissionHubNavigator {
    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayIntent(packageName: String): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName"),
    )

    fun batteryIntent(packageName: String): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
    } else {
        securityIntent()
    }

    fun deviceAdminIntent(): Intent = Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun securityIntent(): Intent = Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun appDetailsIntent(packageName: String): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName"),
    )
}
