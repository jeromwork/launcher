package com.launcher.adapters.wizard.handlers

import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.PermissionRequestPort
import com.launcher.api.wizard.PermissionResult
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.handlers.ApplyHandler

/**
 * `ApplySpec.StandardPermissionRequest` → delegates to
 * [PermissionRequestPort.request].
 */
class AndroidStandardPermissionApplyHandler(
    private val permissionRequestPort: PermissionRequestPort,
) : ApplyHandler {
    override suspend fun apply(spec: ApplySpec): ApplyResult {
        val permission = (spec as? ApplySpec.StandardPermissionRequest)?.permission
            ?: return ApplyResult.UnsupportedMechanism
        return when (permissionRequestPort.request(permission)) {
            PermissionResult.Granted -> ApplyResult.Applied
            PermissionResult.Denied -> ApplyResult.Denied
            PermissionResult.PermanentlyDenied -> ApplyResult.PermanentlyDenied
        }
    }
}
