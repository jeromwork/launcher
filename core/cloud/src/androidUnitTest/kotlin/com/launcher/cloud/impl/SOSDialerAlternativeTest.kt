package com.launcher.cloud.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.launcher.cloud.api.ActionContext
import com.launcher.cloud.api.ActionResult
import com.launcher.cloud.fake.FakeEmergencyNumberResolver
import io.mockk.slot
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SOSDialerAlternativeTest {

    @Test
    fun `executeLocally fires ACTION_DIAL with resolved tel URI`() = runTest {
        val ctx = mockk<Context>(relaxed = true)
        val resolver = FakeEmergencyNumberResolver(number = "112")
        val impl = SOSDialerAlternative(resolver, ctx)

        val captured = slot<Intent>()
        impl.executeLocally(ActionContext(callerId = "sos"))

        verify { ctx.startActivity(capture(captured)) }
        assertEquals(Intent.ACTION_DIAL, captured.captured.action)
        assertEquals(Uri.parse("tel:112"), captured.captured.data)
    }

    @Test
    fun `executeLocally returns Success`() = runTest {
        val ctx = mockk<Context>(relaxed = true)
        val resolver = FakeEmergencyNumberResolver(number = "911")
        val impl = SOSDialerAlternative(resolver, ctx)

        val result = impl.executeLocally(ActionContext(callerId = "sos"))

        assertTrue(result is ActionResult.Success)
    }

    @Test
    fun `executeLocally adds NEW_TASK flag`() = runTest {
        val ctx = mockk<Context>(relaxed = true)
        val resolver = FakeEmergencyNumberResolver(number = "112")
        val impl = SOSDialerAlternative(resolver, ctx)

        val captured = slot<Intent>()
        impl.executeLocally(ActionContext(callerId = "sos"))
        verify { ctx.startActivity(capture(captured)) }

        assertTrue(
            captured.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
            "Intent must have FLAG_ACTIVITY_NEW_TASK",
        )
    }
}
