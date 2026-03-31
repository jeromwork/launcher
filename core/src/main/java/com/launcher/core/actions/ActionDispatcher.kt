package com.launcher.core.actions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.launcher.api.ActionRequest
import com.launcher.api.BlockReason
import com.launcher.api.DispatchResult
import com.launcher.api.SystemSettingsTarget
import com.launcher.core.catalog.AppIndex

/**
 * Single pipeline for launch and settings intents (contracts/actions.md).
 */
class ActionDispatcher(
    private val context: Context,
    private val appIndex: AppIndex,
) {

    fun dispatch(request: ActionRequest): DispatchResult {
        return when (request) {
            is ActionRequest.OpenApplication -> dispatchOpenApplication(request.catalogStableKey)
            is ActionRequest.OpenSystemSettings -> dispatchSettings(request.target)
        }
    }

    private fun dispatchOpenApplication(stableKey: String): DispatchResult {
        if (stableKey.isBlank()) {
            return DispatchResult.BlockedByPolicy(BlockReason.INVALID_REQUEST)
        }
        val entry = appIndex.findEntry(stableKey)
            ?: return DispatchResult.BlockedByPolicy(BlockReason.NOT_IN_CATALOG)
        if (!entry.isLaunchable) {
            return DispatchResult.BlockedByPolicy(BlockReason.NOT_LAUNCHABLE)
        }
        val pm = context.packageManager
        val launch = pm.getLaunchIntentForPackage(stableKey)
            ?: return DispatchResult.Failure("missing_launch_intent")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(launch)
        }.onFailure {
            return DispatchResult.BlockedByPolicy(BlockReason.PERMISSION_OR_POLICY)
        }
        return DispatchResult.Ok
    }

    private fun dispatchSettings(target: SystemSettingsTarget): DispatchResult {
        val intent = when (target) {
            SystemSettingsTarget.General -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                return DispatchResult.BlockedByPolicy(BlockReason.PERMISSION_OR_POLICY)
            }
        return DispatchResult.Ok
    }
}
