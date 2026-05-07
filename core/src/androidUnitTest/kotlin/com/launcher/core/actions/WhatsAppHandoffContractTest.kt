package com.launcher.core.actions

import android.content.Context
import com.launcher.api.ActionRequest
import com.launcher.api.CommunicationActionType
import com.launcher.api.DispatchResult
import com.launcher.api.WhatsAppHandoffRequest
import com.launcher.api.WhatsAppHandoffResult
import com.launcher.core.catalog.AppIndex
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppHandoffContractTest {

    @Test
    fun duplicateCycleIsRejected() {
        val context = mockk<Context>(relaxed = true)
        val index = mockk<AppIndex>(relaxed = true)
        val guard = ActionCycleGuard()
        val dispatcher = ActionDispatcher(
            context = context,
            appIndex = index,
            actionCycleGuard = guard,
            configValidator = mockk(relaxed = true),
            launchabilityResolver = mockk(relaxed = true),
        )

        guard.beginCycle("tile_anna", "cycle-1")
        val result = dispatcher.dispatch(
            ActionRequest.WhatsAppHandoff(
                WhatsAppHandoffRequest(
                    tileId = "tile_anna",
                    contactRef = "contact_anna",
                    actionType = CommunicationActionType.CALL,
                    actionCycleId = "cycle-2",
                    homeSurfaceRef = "home_main",
                ),
            ),
        )

        assertEquals(
            DispatchResult.WhatsApp(WhatsAppHandoffResult.REJECTED_DUPLICATE_CYCLE),
            result,
        )
    }
}

