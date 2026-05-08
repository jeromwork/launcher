package com.launcher.core.flows

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.FlowPreset
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.ProviderId
import com.launcher.api.action.WhatsAppCallKind
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
        val action = anna.action ?: error("expected non-placeholder action for slot_anna_call")
        assertEquals(ProviderId.WHATSAPP, action.providerId)
        val payload = action.payload as ActionPayload.WhatsAppCall
        assertEquals("contact_anna", payload.contactRef)
        assertEquals(WhatsAppCallKind.VOICE, payload.kind)
        // Fallback chain present: phone-call to Anna's number.
        val fallbackPayload = action.fallback?.payload as? ActionPayload.Phone
            ?: error("expected phone fallback")
        assertEquals("+79991234001", fallbackPayload.number)
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
