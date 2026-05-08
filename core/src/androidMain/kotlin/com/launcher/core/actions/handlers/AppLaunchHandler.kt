package com.launcher.core.actions.handlers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.core.actions.PlayStoreFallbackResolver

/**
 * Handles `open_app` payloads (spec 005 US-506).
 *
 * Strategy:
 *  - Primary: `PackageManager.getLaunchIntentForPackage(pkg)`. If the package
 *    is installed and exposes a launcher activity, this is the canonical path.
 *  - If `getLaunchIntentForPackage` returns null AND `storeUrlHint` is set,
 *    open the store URL directly (`market://...` first, web fallback inline).
 *  - If neither path works, return `Failure` so the dispatcher can climb to
 *    the action's own fallback chain.
 *
 * The handler does not consult [PlayStoreFallbackResolver] at runtime — the
 * resolver builds Action chains *at config authoring time* (mock JSON, future
 * backend). Inline `storeUrlHint` is the per-call quick path.
 */
class AppLaunchHandler : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val payload = action.payload as? ActionPayload.OpenApp
            ?: return DispatchResult.Failure(
                "AppLaunchHandler received unexpected payload: ${action.payload::class.simpleName}"
            )

        val launch = ctx.packageManager.getLaunchIntentForPackage(payload.packageHint)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                ctx.context.startActivity(launch)
                DispatchResult.Ok
            } catch (e: ActivityNotFoundException) {
                DispatchResult.Failure("launch intent for '${payload.packageHint}' was rejected: ${e.message}")
            }
        }

        val storeHint = payload.storeUrlHint
        if (storeHint != null) {
            val uri = runCatching { Uri.parse(storeHint) }
                .getOrElse { return DispatchResult.Failure("malformed storeUrlHint '$storeHint'") }
            val storeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                ctx.context.startActivity(storeIntent)
                DispatchResult.Ok
            } catch (e: ActivityNotFoundException) {
                DispatchResult.Failure("no app for store hint '$storeHint': ${e.message}")
            }
        }

        return DispatchResult.Failure("package '${payload.packageHint}' not installed and no storeUrlHint")
    }
}
