package com.launcher.adapters.wizard.handlers

import com.launcher.api.wizard.PermissionRequestPort
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler

/**
 * `CheckSpec.AndroidPermission` → delegates to [PermissionRequestPort].
 * Permission semantics already live in that port (handles API quirks and
 * test fakes); we just route the spec.
 */
class AndroidPermissionCheckHandler(
    private val permissionRequestPort: PermissionRequestPort,
) : CheckHandler {
    override suspend fun check(spec: CheckSpec): SettingStatus {
        val permission = (spec as? CheckSpec.AndroidPermission)?.permission
            ?: return SettingStatus.NotSupportedOnPlatform
        return if (permissionRequestPort.isGranted(permission)) {
            SettingStatus.Applied
        } else {
            SettingStatus.NotApplied
        }
    }
}
