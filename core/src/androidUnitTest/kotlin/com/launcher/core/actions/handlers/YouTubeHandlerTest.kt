package com.launcher.core.actions.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderId
import com.launcher.api.action.YouTubeTarget
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class YouTubeHandlerTest {

    private val baseCtx: Context get() = ApplicationProvider.getApplicationContext()

    private fun ctxWithCapture(): Pair<Context, CapturingSlot<Intent>> {
        val ctx = spyk(baseCtx)
        val intentSlot = slot<Intent>()
        every { ctx.startActivity(capture(intentSlot)) } answers { }
        return ctx to intentSlot
    }

    private fun handlerCtx(ctx: Context) = HandlerContext(
        context = ctx,
        packageManager = ctx.packageManager,
        eventRouter = mockk(relaxed = true),
    )

    @Test
    fun video_webFallback_whenYouTubeAppMissing() = runTest {
        // Robolectric default: no com.google.android.youtube → primary
        // intent.resolveActivity returns null → web fallback fires.
        val (ctx, captured) = ctxWithCapture()
        val r = YouTubeHandler().handle(
            Action(
                providerId = ProviderId.YOUTUBE,
                payload = ActionPayload.YouTube(YouTubeTarget.Video("dQw4w9WgXcQ")),
            ),
            handlerCtx(ctx),
        )
        assertEquals(DispatchResult.Ok, r)
        val expected = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue("expected web fallback intent, got ${captured.captured}",
            expected.filterEquals(captured.captured))
    }

    @Test
    fun home_webFallback() = runTest {
        val (ctx, captured) = ctxWithCapture()
        YouTubeHandler().handle(
            Action(providerId = ProviderId.YOUTUBE, payload = ActionPayload.YouTube(YouTubeTarget.Home)),
            handlerCtx(ctx),
        )
        val expected = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/"))
        assertTrue(expected.filterEquals(captured.captured))
    }

    @Test
    fun channel_byHandle_webFallback() = runTest {
        val (ctx, captured) = ctxWithCapture()
        YouTubeHandler().handle(
            Action(
                providerId = ProviderId.YOUTUBE,
                payload = ActionPayload.YouTube(YouTubeTarget.Channel("@example")),
            ),
            handlerCtx(ctx),
        )
        val expected = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@example"))
        assertTrue(expected.filterEquals(captured.captured))
    }
}
