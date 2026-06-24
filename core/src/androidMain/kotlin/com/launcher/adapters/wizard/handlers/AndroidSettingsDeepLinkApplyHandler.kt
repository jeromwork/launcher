package com.launcher.adapters.wizard.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.handlers.ApplyHandler

/**
 * `ApplySpec.SettingsDeepLink` → starts an `Intent(action)`, optionally
 * scoped to the current package via `package:` URI. Used for
 * `ACCESSIBILITY_SETTINGS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, etc.
 */
class AndroidSettingsDeepLinkApplyHandler(
    private val context: Context,
) : ApplyHandler {
    override suspend fun apply(spec: ApplySpec): ApplyResult {
        val deepLink = spec as? ApplySpec.SettingsDeepLink
            ?: return ApplyResult.UnsupportedMechanism
        val intent = Intent(deepLink.action).apply {
            if (deepLink.packageScoped) {
                data = Uri.parse("package:${context.packageName}")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            ApplyResult.PromptShown
        }.getOrElse { ApplyResult.Failed(it.message ?: "startActivity failed") }
    }
}
