package com.launcher.core.actions.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderId
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
class BrowserHandlerTest {

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
    fun httpsUrl_actionView() = runTest {
        val (ctx, captured) = ctxWithCapture()
        val r = BrowserHandler().handle(
            Action(providerId = ProviderId.BROWSER, payload = ActionPayload.Url("https://example.com/x")),
            handlerCtx(ctx),
        )
        assertEquals(DispatchResult.Ok, r)
        val expected = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/x"))
        assertTrue(expected.filterEquals(captured.captured))
    }

    @Test
    fun httpUrl_isAccepted() = runTest {
        val (ctx, _) = ctxWithCapture()
        val r = BrowserHandler().handle(
            Action(providerId = ProviderId.BROWSER, payload = ActionPayload.Url("http://example.com")),
            handlerCtx(ctx),
        )
        assertEquals(DispatchResult.Ok, r)
    }

    @Test
    fun javascriptScheme_isRejected() = runTest {
        val r = BrowserHandler().handle(
            Action(providerId = ProviderId.BROWSER, payload = ActionPayload.Url("javascript:alert(1)")),
            handlerCtx(spyk(baseCtx)),
        )
        assertTrue(r is DispatchResult.Failure)
        val msg = (r as DispatchResult.Failure).reason
        assertTrue(msg, msg.contains("invalid scheme"))
    }

    @Test
    fun fileScheme_isRejected() = runTest {
        val r = BrowserHandler().handle(
            Action(providerId = ProviderId.BROWSER, payload = ActionPayload.Url("file:///etc/passwd")),
            handlerCtx(spyk(baseCtx)),
        )
        assertTrue(r is DispatchResult.Failure)
    }

    @Test
    fun customScheme_isRejected() = runTest {
        val r = BrowserHandler().handle(
            Action(providerId = ProviderId.BROWSER, payload = ActionPayload.Url("myapp://x")),
            handlerCtx(spyk(baseCtx)),
        )
        assertTrue(r is DispatchResult.Failure)
    }
}
