package com.launcher.core.flows

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.CommunicationActionType
import com.launcher.api.SlotAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MockFlowRepositoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun loadsFlowsFromMockAsset() = runTest {
        val repo = MockFlowRepository(context)
        val flows = repo.loadFlows()

        assertEquals(1, flows.size)
        val family = flows.first()
        assertEquals("flow_family", family.id)
        assertEquals(1, family.schemaVersion)
        assertEquals("contacts", family.templateId)
        assertEquals(2, family.slots.size)
    }

    @Test
    fun slotsHaveCorrectActions() = runTest {
        val repo = MockFlowRepository(context)
        val slots = repo.loadFlows().first().slots

        val anna = slots.first { it.id == "slot_anna_call" }
        val actionAnna = anna.action as SlotAction.WhatsAppCall
        assertEquals("contact_anna", actionAnna.contactRef)
        assertEquals(CommunicationActionType.CALL, actionAnna.actionType)

        val oleg = slots.first { it.id == "slot_oleg_call" }
        val actionOleg = oleg.action as SlotAction.WhatsAppCall
        assertEquals("contact_oleg", actionOleg.contactRef)
        assertEquals(CommunicationActionType.CALL, actionOleg.actionType)
    }

    @Test
    fun returnsEmptyListOnInvalidJson() = runTest {
        val repo = MockFlowRepository(context, assetFileName = "nonexistent_file.json")
        assertTrue(repo.loadFlows().isEmpty())
    }

    @Test
    fun availableTemplatesFiltersByPreset() {
        val repo = MockFlowRepository(context)
        val seniorTemplates = repo.availableTemplates("senior-launcher")
        val adminTemplates = repo.availableTemplates("flow-light")

        assertTrue(seniorTemplates.any { it.id == "contacts" })
        assertTrue(seniorTemplates.none { it.id == "admin_devices" })

        assertTrue(adminTemplates.any { it.id == "contacts" })
        assertTrue(adminTemplates.any { it.id == "admin_devices" })
    }
}
