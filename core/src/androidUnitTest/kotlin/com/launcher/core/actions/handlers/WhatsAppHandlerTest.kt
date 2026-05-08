package com.launcher.core.actions.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderId
import com.launcher.api.action.WhatsAppCallKind
import com.launcher.core.contacts.MockContact
import com.launcher.core.contacts.MockContactsRepository
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
class WhatsAppHandlerTest {

    private val baseCtx: Context get() = ApplicationProvider.getApplicationContext()

    private fun contactsWith(refToPhone: Map<String, String>): MockContactsRepository {
        val repo = mockk<MockContactsRepository>()
        for ((ref, phone) in refToPhone) {
            every { repo.findByRef(ref) } returns MockContact(ref = ref, displayName = ref, phoneE164 = phone)
        }
        every { repo.findByRef(match { it !in refToPhone }) } returns null
        return repo
    }

    private fun pmThatResolves(vararg packages: String): PackageManager {
        val pm = mockk<PackageManager>()
        every { pm.resolveActivity(any(), 0) } answers {
            val intent = firstArg<Intent>()
            val pkg = intent.`package`
            if (pkg in packages) ResolveInfo() else null
        }
        return pm
    }

    private fun ctxAndCapture(): Pair<Context, CapturingSlot<Intent>> {
        val ctx = spyk(baseCtx)
        val intentSlot = slot<Intent>()
        every { ctx.startActivity(capture(intentSlot)) } answers { }
        return ctx to intentSlot
    }

    private fun hctx(ctx: Context, pm: PackageManager) = HandlerContext(
        context = ctx,
        packageManager = pm,
        eventRouter = mockk(relaxed = true),
    )

    @Test
    fun message_via_comWhatsapp_uri_isWaMe() = runTest {
        val (ctx, captured) = ctxAndCapture()
        val pm = pmThatResolves("com.whatsapp")
        val r = WhatsAppHandler(contactsWith(mapOf("alice" to "+12025550101"))).handle(
            Action(providerId = ProviderId.WHATSAPP, payload = ActionPayload.WhatsAppMessage("alice")),
            hctx(ctx, pm),
        )
        assertEquals(DispatchResult.Ok, r)
        assertEquals("com.whatsapp", captured.captured.`package`)
        assertEquals("https://wa.me/12025550101", captured.captured.dataString)
    }

    @Test
    fun message_via_w4b_when_stockMissing() = runTest {
        val (ctx, captured) = ctxAndCapture()
        val pm = pmThatResolves("com.whatsapp.w4b")  // only business installed
        val r = WhatsAppHandler(contactsWith(mapOf("bob" to "+12025550102"))).handle(
            Action(providerId = ProviderId.WHATSAPP, payload = ActionPayload.WhatsAppMessage("bob")),
            hctx(ctx, pm),
        )
        assertEquals(DispatchResult.Ok, r)
        assertEquals("com.whatsapp.w4b", captured.captured.`package`)
    }

    @Test
    fun voiceCall_buildsWaMeUri() = runTest {
        val (ctx, captured) = ctxAndCapture()
        val pm = pmThatResolves("com.whatsapp")
        val r = WhatsAppHandler(contactsWith(mapOf("alice" to "+12025550101"))).handle(
            Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppCall("alice", WhatsAppCallKind.VOICE),
            ),
            hctx(ctx, pm),
        )
        assertEquals(DispatchResult.Ok, r)
        assertEquals("https://wa.me/12025550101", captured.captured.dataString)
    }

    @Test
    fun videoCall_buildsWaMeUri_kindRecordedOnAction() = runTest {
        // wa.me has no public deep-link to dial directly; voice/video share URI.
        // Test still asserts dispatch succeeds.
        val (ctx, _) = ctxAndCapture()
        val pm = pmThatResolves("com.whatsapp")
        val r = WhatsAppHandler(contactsWith(mapOf("bob" to "+12025550102"))).handle(
            Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppCall("bob", WhatsAppCallKind.VIDEO),
            ),
            hctx(ctx, pm),
        )
        assertEquals(DispatchResult.Ok, r)
    }

    @Test
    fun unknownContactRef_returnsFailure() = runTest {
        val (ctx, _) = ctxAndCapture()
        val pm = pmThatResolves("com.whatsapp")
        val r = WhatsAppHandler(contactsWith(emptyMap())).handle(
            Action(providerId = ProviderId.WHATSAPP, payload = ActionPayload.WhatsAppMessage("ghost")),
            hctx(ctx, pm),
        )
        assertTrue(r is DispatchResult.Failure)
        assertTrue((r as DispatchResult.Failure).reason.contains("ghost"))
    }

    @Test
    fun noWhatsAppPackage_returnsFailure() = runTest {
        val (ctx, _) = ctxAndCapture()
        val pm = pmThatResolves(/* nothing */)
        val r = WhatsAppHandler(contactsWith(mapOf("alice" to "+1"))).handle(
            Action(providerId = ProviderId.WHATSAPP, payload = ActionPayload.WhatsAppMessage("alice")),
            hctx(ctx, pm),
        )
        assertTrue(r is DispatchResult.Failure)
    }
}
