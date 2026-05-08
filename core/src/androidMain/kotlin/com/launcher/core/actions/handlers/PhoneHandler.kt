package com.launcher.core.actions.handlers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult

/**
 * Handles `phone` payloads (spec 005 US-502).
 *
 * Uses `Intent.ACTION_DIAL` with `tel:<number>` URI — opens the dialer
 * pre-filled with the number; the user must press the green button to
 * actually call. **No `CALL_PHONE` runtime permission** (spec §7.5).
 * `ACTION_CALL` would call automatically and require permission; that is
 * forbidden in spec 005 to keep the elderly user from accidental calls.
 *
 * The grep test in `PhoneHandlerTest` enforces `CALL_PHONE` is not
 * referenced anywhere in this file.
 */
class PhoneHandler : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val payload = action.payload as? ActionPayload.Phone
            ?: return DispatchResult.Failure(
                "PhoneHandler received unexpected payload: ${action.payload::class.simpleName}"
            )

        val intent = Intent(Intent.ACTION_DIAL, Uri.parse(TEL_SCHEME + payload.number)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.context.startActivity(intent)
            DispatchResult.Ok
        } catch (e: ActivityNotFoundException) {
            DispatchResult.Failure("no dialer for tel: (${e.message ?: "ActivityNotFoundException"})")
        }
    }

    companion object {
        private const val TEL_SCHEME = "tel:"
    }
}
