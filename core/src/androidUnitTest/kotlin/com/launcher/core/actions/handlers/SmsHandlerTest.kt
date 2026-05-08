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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsHandlerTest {

    private val baseCtx: Context get() = ApplicationProvider.getApplicationContext()

    private fun captureStartActivity(): Pair<Context, CapturingSlot<Intent>> {
        val ctx = spyk(baseCtx)
        val intentSlot = slot<Intent>()
        every { ctx.startActivity(capture(intentSlot)) } answers { /* no-op */ }
        return ctx to intentSlot
    }

    private fun handlerCtx(ctx: Context) = HandlerContext(
        context = ctx,
        packageManager = ctx.packageManager,
        eventRouter = mockk(relaxed = true),
    )

    @Test
    fun smsIntent_actionSendto_smsto_withBodyExtra() = runTest {
        val (ctx, captured) = captureStartActivity()
        val result = SmsHandler().handle(
            Action(
                providerId = ProviderId.SMS,
                payload = ActionPayload.Sms("+79991234567", body = "ping"),
            ),
            handlerCtx(ctx),
        )

        assertEquals(DispatchResult.Ok, result)
        val expected = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+79991234567"))
        assertTrue(expected.filterEquals(captured.captured))
        assertEquals("ping", captured.captured.getStringExtra("sms_body"))
    }

    @Test
    fun smsIntent_actionSendto_noBody_omitsExtra() = runTest {
        val (ctx, captured) = captureStartActivity()
        SmsHandler().handle(
            Action(providerId = ProviderId.SMS, payload = ActionPayload.Sms("+79991234567")),
            handlerCtx(ctx),
        )
        assertNull(captured.captured.getStringExtra("sms_body"))
    }

    @Test
    fun unexpectedPayload_returnsFailure() = runTest {
        val (ctx, _) = captureStartActivity()
        val result = SmsHandler().handle(
            Action(providerId = ProviderId.SMS, payload = ActionPayload.Phone("+1")),
            handlerCtx(ctx),
        )
        assertTrue(result is DispatchResult.Failure)
    }
}
