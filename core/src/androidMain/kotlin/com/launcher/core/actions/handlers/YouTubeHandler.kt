package com.launcher.core.actions.handlers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.YouTubeTarget

/**
 * Handles `youtube` payloads (spec 005 US-504).
 *
 * Strategy:
 *  - Primary: `vnd.youtube:` for [YouTubeTarget.Video] (the YouTube app
 *    handles this scheme; it skips the chooser dialog when installed).
 *  - For [YouTubeTarget.Home] / [YouTubeTarget.Channel] there is no native
 *    deep-link, so we use `https://youtube.com/...` and let YouTube's
 *    intent-filter claim the URL.
 *  - On `ActivityNotFoundException` we fall back to the web URL with a
 *    plain `ACTION_VIEW` so the OS picks any browser.
 */
class YouTubeHandler : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val payload = action.payload as? ActionPayload.YouTube
            ?: return DispatchResult.Failure(
                "YouTubeHandler received unexpected payload: ${action.payload::class.simpleName}"
            )

        val (primary, web) = when (val target = payload.target) {
            YouTubeTarget.Home -> Uri.parse(WEB_HOME) to Uri.parse(WEB_HOME)
            is YouTubeTarget.Video -> Uri.parse(VND_VIDEO + target.videoId) to
                Uri.parse(WEB_VIDEO + target.videoId)
            is YouTubeTarget.Channel -> {
                val handle = target.channelHandle.removePrefix("@")
                Uri.parse(WEB_CHANNEL + handle) to Uri.parse(WEB_CHANNEL + handle)
            }
        }

        val primaryIntent = Intent(Intent.ACTION_VIEW, primary).apply {
            setPackage(YOUTUBE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (ctx.packageManager.resolveActivity(primaryIntent, 0) != null) {
            try {
                ctx.context.startActivity(primaryIntent)
                return DispatchResult.Ok
            } catch (_: ActivityNotFoundException) {
                // fall through to web fallback
            }
        }

        val webIntent = Intent(Intent.ACTION_VIEW, web).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.context.startActivity(webIntent)
            DispatchResult.Ok
        } catch (e: ActivityNotFoundException) {
            DispatchResult.Failure("no app can handle youtube URI ($web): ${e.message}")
        }
    }

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val VND_VIDEO   = "vnd.youtube:"
        private const val WEB_HOME    = "https://www.youtube.com/"
        private const val WEB_VIDEO   = "https://www.youtube.com/watch?v="
        private const val WEB_CHANNEL = "https://www.youtube.com/@"
    }
}
