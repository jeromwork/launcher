package com.launcher.core.actions.handlers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.SettingsTarget

/**
 * Handles `open_settings` payloads. Single target in v1.0.0 ([SettingsTarget.General]
 * → `Settings.ACTION_SETTINGS`, the top-level system settings entry).
 *
 * Carry-over of the spec 003 `OpenSystemSettings` action through the new
 * dispatcher pipeline. New variants (Wi-Fi, accessibility, etc.) get added as
 * minor wire-format bumps; this handler dispatches them via `when` over
 * [SettingsTarget].
 */
class SystemSettingsHandler : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val payload = action.payload as? ActionPayload.OpenSettings
            ?: return DispatchResult.Failure(
                "SystemSettingsHandler received unexpected payload: ${action.payload::class.simpleName}"
            )

        val intentAction = when (payload.target) {
            SettingsTarget.General -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(intentAction).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.context.startActivity(intent)
            DispatchResult.Ok
        } catch (e: ActivityNotFoundException) {
            DispatchResult.Failure("no system settings activity (${e.message})")
        }
    }
}
