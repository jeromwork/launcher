package com.launcher.core.actions.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderId
import com.launcher.core.events.EventRouter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PhoneHandlerTest {

    private val baseCtx: Context get() = ApplicationProvider.getApplicationContext()

    private fun handlerCtx(spyContext: Context = spyk(baseCtx)): HandlerContext = HandlerContext(
        context = spyContext,
        packageManager = spyContext.packageManager,
        eventRouter = mockk(relaxed = true),
    )

    @Test
    fun phoneIntent_isActionDial_withTelUri() = runTest {
        val ctx = spyk(baseCtx)
        val captured = slot<Intent>()
        every { ctx.startActivity(capture(captured)) } answers { /* no-op */ }

        val handler = PhoneHandler()
        val result = handler.handle(
            Action(providerId = ProviderId.PHONE, payload = ActionPayload.Phone("+74951234567")),
            handlerCtx(ctx),
        )

        assertEquals(DispatchResult.Ok, result)
        val expected = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+74951234567"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(
            "expected filterEquals against ACTION_DIAL+tel: but got ${captured.captured}",
            expected.filterEquals(captured.captured),
        )
        assertEquals(Intent.ACTION_DIAL, captured.captured.action)
    }

    @Test
    fun unexpectedPayload_returnsFailure() = runTest {
        val handler = PhoneHandler()
        val result = handler.handle(
            Action(providerId = ProviderId.PHONE, payload = ActionPayload.Url("https://example.com")),
            handlerCtx(),
        )
        assertTrue(result is DispatchResult.Failure)
    }

    /**
     * Spec 010 FR-012 update: PhoneHandler now uses ACTION_CALL when
     * CALL_PHONE is granted (user explicitly consented via the spec 010
     * CallConfirmationDialog), falls back to ACTION_DIAL otherwise. The
     * spec-005 grep invariant has been retired and replaced with a structural
     * check that the conditional branch exists.
     */
    @Test
    fun handlerSource_supports_conditional_action_call() {
        val source = File("src/androidMain/kotlin/com/launcher/core/actions/handlers/PhoneHandler.kt")
            .readText()
        val codeOnly = source.lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
            .let { it.replace(Regex("(?s)/\\*.*?\\*/"), "") }
        assertTrue(
            "Spec 010 FR-012: PhoneHandler must reference Manifest.permission.CALL_PHONE",
            codeOnly.contains("permission.CALL_PHONE"),
        )
        assertTrue(
            "Spec 010 FR-012: PhoneHandler must use Intent.ACTION_CALL",
            codeOnly.contains("ACTION_CALL"),
        )
        assertTrue(
            "Spec 010 FR-012: PhoneHandler must retain ACTION_DIAL fallback",
            codeOnly.contains("ACTION_DIAL"),
        )
    }
}
