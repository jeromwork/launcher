package com.launcher.core.actions.handlers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult

/**
 * Handles `sms` payloads (spec 005 US-503).
 *
 * Uses `Intent.ACTION_SENDTO` with `smsto:<number>` URI — opens the system's
 * default SMS app pre-filled with the number (and optional body). No
 * permission needed; the user must press send.
 */
class SmsHandler : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val payload = action.payload as? ActionPayload.Sms
            ?: return DispatchResult.Failure(
                "SmsHandler received unexpected payload: ${action.payload::class.simpleName}"
            )

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(SMSTO_SCHEME + payload.number)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            payload.body?.takeIf { it.isNotEmpty() }?.let { putExtra("sms_body", it) }
        }
        return try {
            ctx.context.startActivity(intent)
            DispatchResult.Ok
        } catch (e: ActivityNotFoundException) {
            DispatchResult.Failure("no sms app for smsto: (${e.message ?: "ActivityNotFoundException"})")
        }
    }

    companion object {
        private const val SMSTO_SCHEME = "smsto:"
    }
}
