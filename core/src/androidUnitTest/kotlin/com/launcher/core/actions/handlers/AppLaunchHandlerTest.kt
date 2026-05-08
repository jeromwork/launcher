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
class AppLaunchHandlerTest {

    private val baseCtx: Context get() = ApplicationProvider.getApplicationContext()

    private fun handlerCtx(ctx: Context, pm: PackageManager) = HandlerContext(
        context = ctx,
        packageManager = pm,
        eventRouter = mockk(relaxed = true),
    )

    @Test
    fun installedPackage_launchIntentStarted() = runTest {
        val ctx = spyk(baseCtx)
        val pm = mockk<PackageManager>()
        val launch = Intent(Intent.ACTION_MAIN).setPackage("com.example").addCategory(Intent.CATEGORY_LAUNCHER)
        every { pm.getLaunchIntentForPackage("com.example") } returns launch

        val captured: CapturingSlot<Intent> = slot()
        every { ctx.startActivity(capture(captured)) } answers { }

        val r = AppLaunchHandler().handle(
            Action(providerId = ProviderId.APP, payload = ActionPayload.OpenApp("com.example")),
            handlerCtx(ctx, pm),
        )
        assertEquals(DispatchResult.Ok, r)
        assertEquals("com.example", captured.captured.`package`)
    }

    @Test
    fun missingPackage_storeUrlHint_isUsed() = runTest {
        val ctx = spyk(baseCtx)
        val pm = mockk<PackageManager>()
        every { pm.getLaunchIntentForPackage("com.example") } returns null

        val captured: CapturingSlot<Intent> = slot()
        every { ctx.startActivity(capture(captured)) } answers { }

        val r = AppLaunchHandler().handle(
            Action(
                providerId = ProviderId.APP,
                payload = ActionPayload.OpenApp(
                    packageHint = "com.example",
                    storeUrlHint = "market://details?id=com.example",
                ),
            ),
            handlerCtx(ctx, pm),
        )
        assertEquals(DispatchResult.Ok, r)
        val expected = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.example"))
        assertTrue(expected.filterEquals(captured.captured))
    }

    @Test
    fun missingPackage_noHint_returnsFailure() = runTest {
        val ctx = spyk(baseCtx)
        val pm = mockk<PackageManager>()
        every { pm.getLaunchIntentForPackage("com.example") } returns null
        val r = AppLaunchHandler().handle(
            Action(providerId = ProviderId.APP, payload = ActionPayload.OpenApp("com.example")),
            handlerCtx(ctx, pm),
        )
        assertTrue(r is DispatchResult.Failure)
    }
}
