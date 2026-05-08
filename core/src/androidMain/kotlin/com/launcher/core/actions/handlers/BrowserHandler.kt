package com.launcher.core.actions.handlers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult

/**
 * Handles `url` payloads (spec 005 US-505).
 *
 * Whitelists scheme to `http`/`https` only. Per security CHK-011 the deep-link
 * gate refuses `javascript:`, `file:`, `content:`, custom schemes — those can
 * carry exploits or escape the launcher's trust boundary. The handler returns
 * `Failure("invalid scheme")` for anything else, and the dispatcher decides
 * whether to fall back.
 */
class BrowserHandler : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val payload = action.payload as? ActionPayload.Url
            ?: return DispatchResult.Failure(
                "BrowserHandler received unexpected payload: ${action.payload::class.simpleName}"
            )

        val uri = runCatching { Uri.parse(payload.url) }
            .getOrElse { return DispatchResult.Failure("malformed url '${payload.url}'") }

        val scheme = uri.scheme?.lowercase()
        if (scheme !in ALLOWED_SCHEMES) {
            return DispatchResult.Failure("invalid scheme '$scheme'; allowed: $ALLOWED_SCHEMES")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.context.startActivity(intent)
            DispatchResult.Ok
        } catch (e: ActivityNotFoundException) {
            DispatchResult.Failure("no browser for $uri (${e.message ?: "ActivityNotFoundException"})")
        }
    }

    companion object {
        private val ALLOWED_SCHEMES = setOf("http", "https")
    }
}
