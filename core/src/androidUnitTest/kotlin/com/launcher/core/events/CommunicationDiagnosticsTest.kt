package com.launcher.core.events

import com.launcher.api.CommunicationActionType
import com.launcher.api.CommunicationDiagnosticEventType
import com.launcher.api.ProjectEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationDiagnosticsTest {

    @Test
    fun launchFailureEmitsStableReasonCode() {
        val events = mutableListOf<ProjectEvent>()
        val diagnostics = CommunicationDiagnostics(emitEvent = { events.add(it) })

        diagnostics.launchFailed(
            tileRef = "tile_anna",
            actionType = CommunicationActionType.CALL,
            cycleRef = "cycle-1",
            reasonCode = "whatsapp_unavailable",
        )

        assertEquals(1, events.size)
        val event = events.single()
        assertTrue(event is ProjectEvent.CommunicationDiagnostic)
        val diagnostic = event as ProjectEvent.CommunicationDiagnostic
        assertEquals(CommunicationDiagnosticEventType.WHATSAPP_LAUNCH_FAILED, diagnostic.eventType)
        assertEquals("whatsapp_unavailable", diagnostic.reasonCode)
    }
}

