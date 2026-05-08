package com.launcher.core.actions.handlers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult

/**
 * Handles Telegram via the `Custom` payload escape hatch (research R2). v1.0.0
 * does not promote a first-class `Telegram*` ActionPayload variant — Telegram
 * is far less load-bearing for the elderly persona than WhatsApp, and the
 * launcher's only current Telegram interaction is "open the Telegram app",
 * which doesn't justify a typed payload.
 *
 * Recognised `Custom.key` values:
 *  - `telegram_open` (no params): launch the Telegram app via launch intent.
 *  - `telegram_chat` (params: `username` OR `phone`): deep-link to a chat
 *    using `https://t.me/<username>` or `tg://resolve?domain=<username>`.
 *
 * Anything else → `Failure("unknown telegram key: …")`.
 *
 * Two Telegram packages are tried in order: `org.telegram.messenger` (stock)
 * then `org.telegram.plus` (Plus Messenger).
 */
class TelegramHandler : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val payload = action.payload as? ActionPayload.Custom
            ?: return DispatchResult.Failure(
                "TelegramHandler received unexpected payload: ${action.payload::class.simpleName}"
            )

        return when (payload.key) {
            KEY_OPEN -> openTelegramApp(ctx)
            KEY_CHAT -> openTelegramChat(payload.params, ctx)
            else -> DispatchResult.Failure("unknown telegram key: '${payload.key}'")
        }
    }

    private fun openTelegramApp(ctx: HandlerContext): DispatchResult {
        for (pkg in TELEGRAM_PACKAGES) {
            val launch = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.context.startActivity(launch)
                return DispatchResult.Ok
            } catch (_: ActivityNotFoundException) {
                // try next package
            }
        }
        return DispatchResult.Failure("no telegram package installed")
    }

    private fun openTelegramChat(params: Map<String, String>, ctx: HandlerContext): DispatchResult {
        val username = params["username"]?.removePrefix("@")
        if (username.isNullOrBlank()) {
            return DispatchResult.Failure("telegram_chat requires 'username' param")
        }
        val webUri = Uri.parse(WEB_RESOLVE + username)
        val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.context.startActivity(intent)
            DispatchResult.Ok
        } catch (e: ActivityNotFoundException) {
            DispatchResult.Failure("no app for telegram chat URI ($webUri): ${e.message}")
        }
    }

    companion object {
        const val KEY_OPEN = "telegram_open"
        const val KEY_CHAT = "telegram_chat"
        private val TELEGRAM_PACKAGES = listOf("org.telegram.messenger", "org.telegram.plus")
        private const val WEB_RESOLVE = "https://t.me/"
    }
}
