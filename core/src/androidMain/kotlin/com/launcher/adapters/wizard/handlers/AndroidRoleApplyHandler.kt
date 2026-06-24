package com.launcher.adapters.wizard.handlers

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.handlers.ApplyHandler

/**
 * `ApplySpec.AndroidRoleRequest` → launches
 * [RoleManager.createRequestRoleIntent] via the app context. Returns
 * [ApplyResult.PromptShown] when the intent dispatches; failure cases
 * fall back to [ApplyResult.UnsupportedMechanism] (pre-API 29) or
 * [ApplyResult.Failed].
 */
class AndroidRoleApplyHandler(private val context: Context) : ApplyHandler {
    override suspend fun apply(spec: ApplySpec): ApplyResult {
        val role = (spec as? ApplySpec.AndroidRoleRequest)?.role
            ?: return ApplyResult.UnsupportedMechanism
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ApplyResult.UnsupportedMechanism
        }
        val rm = context.getSystemService(RoleManager::class.java)
            ?: return ApplyResult.UnsupportedMechanism
        val intent = rm.createRequestRoleIntent(role).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            ApplyResult.PromptShown
        }.getOrElse { ApplyResult.Failed(it.message ?: "startActivity failed") }
    }
}
