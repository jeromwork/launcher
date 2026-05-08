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
     * Source-level assertion: phone handler must NEVER actually call code paths
     * that require `CALL_PHONE` (spec §7.5). Greps the source for runtime
     * usages — references to the Android API symbols, not the spec/comment
     * mention. If a future edit imports `Manifest.permission.CALL_PHONE` or
     * starts using `Intent.ACTION_CALL`, this test fails.
     */
    @Test
    fun handlerSource_doesNotReferenceCallPhonePermission() {
        val source = File("src/androidMain/kotlin/com/launcher/core/actions/handlers/PhoneHandler.kt")
            .readText()
        // Strip comment lines so we test code references, not documentation.
        val codeOnly = source.lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
            .let { it.replace(Regex("(?s)/\\*.*?\\*/"), "") }
        assertFalse(
            "PhoneHandler must not reference Manifest.permission.CALL_PHONE",
            codeOnly.contains("permission.CALL_PHONE"),
        )
        assertFalse(
            "PhoneHandler must not use Intent.ACTION_CALL",
            codeOnly.contains("ACTION_CALL"),
        )
    }
}
