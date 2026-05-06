package com.launcher.core.flows

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.CommunicationActionType
import com.launcher.api.FlowPreset
import com.launcher.api.SlotAction
import com.launcher.core.preset.InMemoryPresetRepository
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
    fun loadsSimpleLauncherFlowsByDefault() = runTest {
        val repo = MockFlowRepository(context, InMemoryPresetRepository())
        val flows = repo.loadFlows()

        assertEquals(1, flows.size)
        assertEquals("flow_family", flows.first().id)
        assertEquals(2, flows.first().slots.size)
    }

    @Test
    fun loadsWorkspaceFlowsWhenPresetIsWorkspace() = runTest {
        val repo = MockFlowRepository(context, InMemoryPresetRepository(FlowPreset.WORKSPACE))
        val flows = repo.loadFlows()

        assertEquals(2, flows.size)
        assertTrue(flows.any { it.id == "flow_contacts" })
        assertTrue(flows.any { it.id == "flow_apps" })
    }

    @Test
    fun loadsLauncherFlowsWhenPresetIsLauncher() = runTest {
        val repo = MockFlowRepository(context, InMemoryPresetRepository(FlowPreset.LAUNCHER))
        val flows = repo.loadFlows()

        assertEquals(1, flows.size)
        assertEquals("flow_main", flows.first().id)
        assertEquals(4, flows.first().slots.size)
    }

    @Test
    fun simpleLauncherSlotsHaveCorrectActions() = runTest {
        val repo = MockFlowRepository(context, InMemoryPresetRepository(FlowPreset.SIMPLE_LAUNCHER))
        val slots = repo.loadFlows().first().slots

        val anna = slots.first { it.id == "slot_anna_call" }
        val actionAnna = anna.action as SlotAction.WhatsAppCall
        assertEquals("contact_anna", actionAnna.contactRef)
        assertEquals(CommunicationActionType.CALL, actionAnna.actionType)
    }

    @Test
    fun availableTemplatesFiltersByPreset() {
        val repo = MockFlowRepository(context, InMemoryPresetRepository())
        val workspace = repo.availableTemplates("workspace")
        val simple = repo.availableTemplates("simple-launcher")

        assertTrue(workspace.any { it.id == "contacts" })
        assertTrue(workspace.any { it.id == "admin_devices" })

        assertTrue(simple.any { it.id == "contacts" })
        assertTrue(simple.none { it.id == "admin_devices" })
    }
}
